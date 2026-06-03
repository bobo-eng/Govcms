package gov.cms.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishEnvironment;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishImpactItemRepository;
import gov.cms.admin.repository.PublishJobRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PublishExecutor {

  private final PublishJobRepository publishJobRepository;
  private final PublishArtifactRepository publishArtifactRepository;
  private final PublishImpactItemRepository publishImpactItemRepository;
  private final PortalRenderService portalRenderService;
  private final RenderContextAssembler renderContextAssembler;
  private final ArticleService articleService;
  private final SearchIndexService searchIndexService;
  private final ArticleRepository articleRepository;
  private final CategoryRepository categoryRepository;
  private final TemplateRepository templateRepository;
  private final TemplateBindingRepository templateBindingRepository;
  private final TopicRepository topicRepository;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;
  private final String publishStoragePath;

  public PublishExecutor(PublishJobRepository publishJobRepository,
                         PublishArtifactRepository publishArtifactRepository,
                         PublishImpactItemRepository publishImpactItemRepository,
                         PortalRenderService portalRenderService,
                         RenderContextAssembler renderContextAssembler,
                         ArticleService articleService,
                         SearchIndexService searchIndexService,
                         ArticleRepository articleRepository,
                         CategoryRepository categoryRepository,
                         TemplateRepository templateRepository,
                         TemplateBindingRepository templateBindingRepository,
                         TopicRepository topicRepository,
                         ObjectMapper objectMapper,
                         PlatformTransactionManager transactionManager,
                         @Value("${app.publish.storage-path:./storage/publish}") String publishStoragePath) {
    this.publishJobRepository = publishJobRepository;
    this.publishArtifactRepository = publishArtifactRepository;
    this.publishImpactItemRepository = publishImpactItemRepository;
    this.portalRenderService = portalRenderService;
    this.renderContextAssembler = renderContextAssembler;
    this.articleService = articleService;
    this.searchIndexService = searchIndexService;
    this.articleRepository = articleRepository;
    this.categoryRepository = categoryRepository;
    this.templateRepository = templateRepository;
    this.templateBindingRepository = templateBindingRepository;
    this.topicRepository = topicRepository;
    this.objectMapper = objectMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.publishStoragePath = publishStoragePath;
  }

  public void execute(Long jobId, String environmentName) {
    PublishJob job = publishJobRepository.findById(jobId)
        .orElseThrow(() -> new IllegalArgumentException("Publish job not found: " + jobId));

    String targetEnv = environmentName != null ? environmentName
        : (job.getEnvironment() != null ? job.getEnvironment()
            : PublishEnvironment.PRODUCTION.name().toLowerCase());

    String renderingState = targetEnv.equalsIgnoreCase("staging")
        ? "staging_rendering" : "production_rendering";
    PublishStateMachine.requireTransition(job.getStatus(), renderingState);
    job.setStatus(renderingState);
    job.setStartedAt(LocalDateTime.now());
    publishJobRepository.save(job);

    List<PublishImpactItem> impacts = publishImpactItemRepository.findByJobIdOrderByIdAsc(job.getId());
    List<String> logs = new ArrayList<>();
    logs.add("Job created: #" + job.getId());
    logs.add("Mode: " + job.getMode());
    logs.add("Unit: " + job.getUnitType() + " -> " + job.getUnitIds());

    try {
      transactionTemplate.execute(new TransactionCallbackWithoutResult() {
        @Override
        protected void doInTransactionWithoutResult(TransactionStatus status) {
          try {
            executeJob(job, targetEnv, impacts, logs);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          ParsedSnapshot snapshot = parseSnapshot(job.getSourceSnapshot());
          applyContentStatusAfterSuccess(job, snapshot.unitType, snapshot.mode, snapshot.unitIds, logs);
          syncSearchIndexAfterSuccess(job, snapshot.unitType, snapshot.mode, snapshot.unitIds, logs);

          job.setStatus(targetEnv.equalsIgnoreCase("staging") ? "staging_ready" : "published");
          job.setFinishedAt(LocalDateTime.now());
          if (targetEnv.equalsIgnoreCase("staging")) {
            job.setPreviewToken(UUID.randomUUID().toString().replace("-", ""));
          }
          job.setLogContent(String.join("\n", logs));
          job.setFailureReason(null);
          publishJobRepository.save(job);
        }
      });
    } catch (Exception e) {
      job.setStatus("failed");
      job.setFailureReason(e.getMessage());
      job.setFinishedAt(LocalDateTime.now());
      logs.add("Job failed: " + e.getMessage());
      job.setLogContent(String.join("\n", logs));
      publishJobRepository.save(job);
      throw new RuntimeException("Publish execution failed for job " + jobId, e);
    }
  }

  private void executeJob(PublishJob job, String targetEnv, List<PublishImpactItem> impacts, List<String> logs) throws IOException {
    ParsedSnapshot snapshot = parseSnapshot(job.getSourceSnapshot());
    for (PublishImpactItem impact : impacts) {
      if ("search-index".equals(impact.getPageType())) {
        logs.add("Search index sync queued in service for " + impact.getSummary());
        continue;
      }
      if ("delete".equalsIgnoreCase(impact.getAction())) {
        handleDeleteArtifact(job, targetEnv, impact, logs);
        continue;
      }
      if ("topic-page".equals(impact.getPageType())) {
        throw new IllegalStateException("Current version does not support topic-page formal publishing.");
      }
      Long templateId = resolveTemplateId(job.getSiteId(), impact);
      if (templateId == null) {
        if ("content-detail".equals(impact.getPageType()) || "topic-page".equals(impact.getPageType())) {
          throw new IllegalStateException("No active template for impact path: " + impact.getPath());
        }
        logs.add("Skipped impact due to missing template: " + impact.getPath());
        continue;
      }
      RenderRequest renderRequest = new RenderRequest();
      renderRequest.setSiteId(job.getSiteId());
      renderRequest.setTemplateId(templateId);
      renderRequest.setPageType(impact.getPageType());
      renderRequest.setSourceType(impact.getSourceType());
      renderRequest.setSourceId(impact.getSourceId());
      renderRequest.setMode("publish");
      renderRequest.setOperation(job.getMode());
      if ("content".equals(snapshot.unitType)) {
        if ("incremental".equals(snapshot.mode)) {
          renderRequest.setIncludeArticleIds(snapshot.unitIds);
        }
        if ("offline".equals(snapshot.mode)) {
          renderRequest.setExcludeArticleIds(snapshot.unitIds);
        }
      }

      RenderContextSnapshot contextSnapshot = renderContextAssembler.assemble(renderRequest);
      var renderResult = portalRenderService.render(contextSnapshot);
      Path outputPath = resolveEnvOutputPath(job.getSiteId(), targetEnv, impact.getPath());
      Files.createDirectories(outputPath.getParent());
      String backupPath = backupIfExists(job.getSiteId(), targetEnv, job.getId(), outputPath);
      Files.writeString(outputPath, Optional.ofNullable(renderResult.getRenderedHtml()).orElse(""), StandardCharsets.UTF_8);
      PublishArtifact artifact = new PublishArtifact();
      artifact.setJobId(job.getId());
      artifact.setArtifactType("html");
      artifact.setOutputPath(impact.getPath());
      artifact.setBackupPath(backupPath);
      artifact.setChecksum(sha256(Files.readAllBytes(outputPath)));
      artifact.setVersion(job.getId() + "-" + impact.getId());
      publishArtifactRepository.save(artifact);
      logs.add("Rendered " + impact.getPageType() + " -> " + impact.getPath());
    }
  }

  private void applyContentStatusAfterSuccess(PublishJob job, String unitType, String mode, List<Long> unitIds, List<String> logs) {
    if (!"content".equals(unitType)) {
      return;
    }
    for (Long articleId : unitIds) {
      if ("incremental".equals(mode)) {
        articleService.applyPublishSuccess(articleId, job.getId(), job.getOperatorName());
        logs.add("Marked article as published: " + articleId);
      } else if ("offline".equals(mode)) {
        articleService.applyOfflineSuccess(articleId, "Published through offline job", job.getId(), job.getOperatorName());
        logs.add("Marked article as offline: " + articleId);
      }
    }
  }

  private void syncSearchIndexAfterSuccess(PublishJob job, String unitType, String mode, List<Long> unitIds, List<String> logs) {
    switch (unitType) {
      case "content" -> {
        for (Long articleId : unitIds) {
          if ("offline".equals(mode)) {
            searchIndexService.removeContentEntry(job.getSiteId(), articleId);
            logs.add("Removed content search index: " + articleId);
          } else {
            searchIndexService.syncContentEntry(articleId);
            logs.add("Synced content search index: " + articleId);
          }
        }
      }
      case "category" -> {
        for (Long categoryId : unitIds) {
          searchIndexService.syncCategoryEntry(categoryId);
          for (Article article : articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(job.getSiteId(), categoryId, ArticleStatus.published)) {
            searchIndexService.syncContentEntry(article.getId());
          }
          logs.add("Synced category search index: " + categoryId);
        }
      }
      case "topic" -> {
        for (Long topicId : unitIds) {
          searchIndexService.syncTopicEntry(topicId);
          logs.add("Synced topic search index: " + topicId);
        }
      }
      case "site", "template" -> {
        searchIndexService.rebuildSiteIndex(job.getSiteId());
        logs.add("Rebuilt site search index: site-" + job.getSiteId());
      }
      default -> {
      }
    }
  }

  private Long resolveTemplateId(Long siteId, PublishImpactItem impact) {
    return switch (impact.getPageType()) {
      case "home" -> resolveSiteTemplate(siteId, "site_home");
      case "error-404" -> resolveSiteTemplate(siteId, "site_404");
      case "column-list" -> impact.getSourceId() == null ? null : resolveColumnListTemplate(siteId, impact.getSourceId());
      case "content-detail" -> impact.getSourceId() == null ? null : resolveContentDetailTemplate(siteId, impact.getSourceId());
      default -> null;
    };
  }

  private Long resolveSiteTemplate(Long siteId, String bindingSlot) {
    return Optional.ofNullable(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(siteId, "site", siteId, bindingSlot, "active"))
        .flatMap(bindings -> bindings.stream().findFirst())
        .map(TemplateBinding::getTemplateId)
        .orElse(null);
  }

  private Long resolveColumnListTemplate(Long siteId, Long categoryId) {
    var category = categoryRepository.findByIdAndSiteId(categoryId, siteId).orElse(null);
    if (category == null) {
      return null;
    }
    if (category.getListTemplateId() != null) {
      return category.getListTemplateId();
    }
    return Optional.ofNullable(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(siteId, "column", categoryId, "column_list", "active"))
        .flatMap(bindings -> bindings.stream().findFirst())
        .map(TemplateBinding::getTemplateId)
        .orElse(null);
  }

  private Long resolveContentDetailTemplate(Long siteId, Long articleId) {
    Article article = articleRepository.findById(articleId).orElse(null);
    if (article == null) {
      return null;
    }
    var category = article.getPrimaryCategoryId() == null ? null : categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), siteId).orElse(null);
    if (category != null && category.getDetailTemplateId() != null) {
      return category.getDetailTemplateId();
    }
    if (category != null) {
      Optional<TemplateBinding> categoryBinding = Optional.ofNullable(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(siteId, "column", category.getId(), "column_detail_default", "active"))
          .flatMap(bindings -> bindings.stream().findFirst());
      if (categoryBinding.isPresent()) {
        return categoryBinding.get().getTemplateId();
      }
    }
    return resolveSiteTemplate(siteId, "site_detail_default");
  }

  private void handleDeleteArtifact(PublishJob job, String targetEnv, PublishImpactItem impact, List<String> logs) throws IOException {
    Path outputPath = resolveEnvOutputPath(job.getSiteId(), targetEnv, impact.getPath());
    String backupPath = backupIfExists(job.getSiteId(), targetEnv, job.getId(), outputPath);
    Files.deleteIfExists(outputPath);
    PublishArtifact artifact = new PublishArtifact();
    artifact.setJobId(job.getId());
    artifact.setArtifactType("delete");
    artifact.setOutputPath(impact.getPath());
    artifact.setBackupPath(backupPath);
    artifact.setVersion(job.getId() + "-" + impact.getId());
    publishArtifactRepository.save(artifact);
    logs.add("Deleted artifact " + impact.getPath());
  }

  private Path resolveOutputRoot(Long siteId, String environment) {
    return Paths.get(publishStoragePath, String.valueOf(siteId), environment.toLowerCase());
  }

  private Path resolveEnvOutputPath(Long siteId, String environment, String logicalPath) {
    String sanitized = logicalPath == null || logicalPath.isBlank() ? "/index.html" : logicalPath;
    String relative = sanitized.startsWith("/") ? sanitized.substring(1) : sanitized;
    return resolveOutputRoot(siteId, environment).resolve(relative);
  }

  private String backupIfExists(Long siteId, String environment, Long jobId, Path outputPath) throws IOException {
    if (!Files.exists(outputPath)) {
      return null;
    }
    Path backupRoot = resolveOutputRoot(siteId, environment).resolve(".backups").resolve("job-" + jobId);
    Path relative = resolveOutputRoot(siteId, environment).relativize(outputPath);
    Path backupPath = backupRoot.resolve(relative.toString() + ".bak");
    Files.createDirectories(backupPath.getParent());
    Files.copy(outputPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
    return backupPath.toString().replace('\\', '/');
  }

  private String sha256(byte[] data) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] digest = messageDigest.digest(data);
      StringBuilder builder = new StringBuilder();
      for (byte value : digest) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private ParsedSnapshot parseSnapshot(String sourceSnapshot) {
    try {
      JsonNode root = objectMapper.readTree(sourceSnapshot);
      ParsedSnapshot snapshot = new ParsedSnapshot();
      snapshot.unitType = root.path("unitType").asText(null);
      snapshot.mode = root.path("mode").asText(null);
      snapshot.unitIds = new ArrayList<>();
      if (root.path("unitIds").isArray()) {
        for (JsonNode node : root.path("unitIds")) {
          snapshot.unitIds.add(node.asLong());
        }
      }
      return snapshot;
    } catch (IOException e) {
      throw new IllegalStateException("Invalid publish snapshot.", e);
    }
  }

  private static class ParsedSnapshot {
    private String unitType;
    private String mode;
    private List<Long> unitIds;
  }
}
