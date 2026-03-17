package gov.cms.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.ArticlePublishCheckResponse;
import gov.cms.admin.dto.PublishCheckResponse;
import gov.cms.admin.dto.PublishImpactResponse;
import gov.cms.admin.dto.PublishRequest;
import gov.cms.admin.dto.PublishRollbackRequest;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.entity.PublishRollbackRecord;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishImpactItemRepository;
import gov.cms.admin.repository.PublishJobRepository;
import gov.cms.admin.repository.PublishRollbackRecordRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PublishService {

    private final PublishJobRepository publishJobRepository;
    private final PublishImpactItemRepository publishImpactItemRepository;
    private final PublishArtifactRepository publishArtifactRepository;
    private final PublishRollbackRecordRepository publishRollbackRecordRepository;
    private final PublishImpactCalculator publishImpactCalculator;
    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final SiteRepository siteRepository;
    private final TemplateRepository templateRepository;
    private final TemplateBindingRepository templateBindingRepository;
    private final ArticleService articleService;
    private final RenderContextAssembler renderContextAssembler;
    private final PortalRenderService portalRenderService;
    private final ObjectMapper objectMapper;
    private String publishStoragePath = "./storage/publish";

    public PublishService(PublishJobRepository publishJobRepository,
                          PublishImpactItemRepository publishImpactItemRepository,
                          PublishArtifactRepository publishArtifactRepository,
                          PublishRollbackRecordRepository publishRollbackRecordRepository,
                          PublishImpactCalculator publishImpactCalculator,
                          ArticleRepository articleRepository,
                          CategoryRepository categoryRepository,
                          SiteRepository siteRepository,
                          TemplateRepository templateRepository,
                          TemplateBindingRepository templateBindingRepository,
                          ArticleService articleService,
                          RenderContextAssembler renderContextAssembler,
                          PortalRenderService portalRenderService,
                          ObjectMapper objectMapper) {
        this.publishJobRepository = publishJobRepository;
        this.publishImpactItemRepository = publishImpactItemRepository;
        this.publishArtifactRepository = publishArtifactRepository;
        this.publishRollbackRecordRepository = publishRollbackRecordRepository;
        this.publishImpactCalculator = publishImpactCalculator;
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
        this.templateRepository = templateRepository;
        this.templateBindingRepository = templateBindingRepository;
        this.articleService = articleService;
        this.renderContextAssembler = renderContextAssembler;
        this.portalRenderService = portalRenderService;
        this.objectMapper = objectMapper;
    }

    public PublishCheckResponse check(PublishRequest request) {
        PublishRequest normalized = normalizeRequest(request);
        PublishCheckResponse response = new PublishCheckResponse();
        response.setSiteId(normalized.getSiteId());
        response.setUnitType(normalized.getUnitType());
        response.setMode(normalized.getMode());
        List<String> reasons = validate(normalized);
        PublishImpactCalculator.ImpactPlan plan = publishImpactCalculator.calculate(normalized);
        response.setWarnings(plan.getWarnings());
        response.setImpactCount(plan.getItems().size());
        response.setReasons(reasons);
        response.setPublishable(reasons.isEmpty() && !plan.getItems().isEmpty());
        if (plan.getItems().isEmpty()) {
            response.getReasons().add("no publish impacts generated");
            response.setPublishable(false);
        }
        return response;
    }

    public PublishImpactResponse impact(PublishRequest request) {
        PublishRequest normalized = normalizeRequest(request);
        PublishImpactCalculator.ImpactPlan plan = publishImpactCalculator.calculate(normalized);
        PublishImpactResponse response = new PublishImpactResponse();
        response.setSiteId(normalized.getSiteId());
        response.setUnitType(normalized.getUnitType());
        response.setMode(normalized.getMode());
        response.setWarnings(plan.getWarnings());
        response.setTotalItems(plan.getItems().size());
        response.setItems(plan.getItems().stream().map(this::toImpactView).collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public PublishJob createAndExecute(PublishRequest request) {
        PublishRequest normalized = normalizeRequest(request);
        PublishCheckResponse check = check(normalized);
        if (!check.isPublishable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("; ", check.getReasons()));
        }

        PublishJob job = new PublishJob();
        job.setSiteId(normalized.getSiteId());
        job.setUnitType(normalized.getUnitType());
        job.setUnitIds(joinIds(normalized.getUnitIds()));
        job.setMode(normalized.getMode());
        job.setStatus("created");
        job.setOperatorName(currentOperatorName());
        job.setOutputRoot(resolveSiteOutputRoot(normalized.getSiteId()).toString().replace('\\', '/'));
        job.setSourceSnapshot(buildSourceSnapshot(normalized));
        job.setResultSummary(normalized.getOperatorComment());
        job = publishJobRepository.save(job);

        PublishImpactCalculator.ImpactPlan impactPlan = publishImpactCalculator.calculate(normalized);
        for (PublishImpactItem item : impactPlan.getItems()) {
            item.setJobId(job.getId());
        }
        publishImpactItemRepository.saveAll(impactPlan.getItems());

        List<String> logs = new ArrayList<>();
        logs.add("Job created: #" + job.getId());
        logs.add("Mode: " + job.getMode());
        logs.add("Unit: " + job.getUnitType() + " -> " + job.getUnitIds());

        job.setStatus("running");
        job.setStartedAt(LocalDateTime.now());
        publishJobRepository.save(job);

        try {
            executeJob(job, impactPlan.getItems(), logs);
            applyContentStatusAfterSuccess(job, normalized, logs);
            job.setStatus("success");
            job.setFinishedAt(LocalDateTime.now());
            job.setLogContent(String.join("\n", logs));
            job.setResultSummary("Generated " + publishArtifactRepository.findByJobIdOrderByIdAsc(job.getId()).size() + " artifact records.");
            job.setFailureReason(null);
            return publishJobRepository.save(job);
        } catch (Exception exception) {
            job.setStatus("failed");
            job.setFinishedAt(LocalDateTime.now());
            job.setFailureReason(exception.getMessage());
            logs.add("Job failed: " + exception.getMessage());
            job.setLogContent(String.join("\n", logs));
            publishJobRepository.save(job);
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    public List<PublishJob> listJobs(Long siteId, String status, String mode) {
        return publishJobRepository.findAll().stream()
                .filter(job -> siteId == null || Objects.equals(job.getSiteId(), siteId))
                .filter(job -> status == null || status.isBlank() || status.equalsIgnoreCase(job.getStatus()))
                .filter(job -> mode == null || mode.isBlank() || mode.equalsIgnoreCase(job.getMode()))
                .sorted((left, right) -> Optional.ofNullable(right.getCreatedAt()).orElse(LocalDateTime.MIN)
                        .compareTo(Optional.ofNullable(left.getCreatedAt()).orElse(LocalDateTime.MIN)))
                .toList();
    }

    public PublishJob getJob(Long id) {
        return publishJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publish job not found."));
    }

    public List<PublishImpactItem> getImpacts(Long jobId) {
        getJob(jobId);
        return publishImpactItemRepository.findByJobIdOrderByIdAsc(jobId);
    }

    public List<PublishArtifact> getArtifacts(Long jobId) {
        getJob(jobId);
        return publishArtifactRepository.findByJobIdOrderByIdAsc(jobId);
    }

    public List<String> getLogs(Long jobId) {
        PublishJob job = getJob(jobId);
        if (job.getLogContent() == null || job.getLogContent().isBlank()) {
            return List.of();
        }
        return List.of(job.getLogContent().split("\\n"));
    }

    @Transactional
    public PublishJob retry(Long jobId) {
        PublishJob job = getJob(jobId);
        if (!"failed".equalsIgnoreCase(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can be retried.");
        }
        return createAndExecute(readRequestFromSnapshot(job.getSourceSnapshot()));
    }

    @Transactional
    public PublishJob rollback(Long jobId, PublishRollbackRequest request) {
        PublishJob targetJob = getJob(jobId);
        if (!("success".equalsIgnoreCase(targetJob.getStatus()) || "rollback_success".equalsIgnoreCase(targetJob.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only successful jobs can be rolled back.");
        }
        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
                ? "Manual rollback"
                : request.getReason().trim();

        PublishJob rollbackJob = new PublishJob();
        rollbackJob.setSiteId(targetJob.getSiteId());
        rollbackJob.setUnitType(targetJob.getUnitType());
        rollbackJob.setUnitIds(targetJob.getUnitIds());
        rollbackJob.setMode("rollback");
        rollbackJob.setStatus("running");
        rollbackJob.setOperatorName(currentOperatorName());
        rollbackJob.setOutputRoot(targetJob.getOutputRoot());
        rollbackJob.setSourceSnapshot(targetJob.getSourceSnapshot());
        rollbackJob.setStartedAt(LocalDateTime.now());
        rollbackJob = publishJobRepository.save(rollbackJob);

        List<String> logs = new ArrayList<>();
        logs.add("Rollback job created: #" + rollbackJob.getId());
        logs.add("Target job: #" + targetJob.getId());
        List<PublishArtifact> targetArtifacts = publishArtifactRepository.findByJobIdOrderByIdAsc(targetJob.getId());
        List<PublishImpactItem> targetImpacts = publishImpactItemRepository.findByJobIdOrderByIdAsc(targetJob.getId());

        try {
            for (PublishImpactItem targetImpact : targetImpacts) {
                PublishImpactItem rollbackImpact = new PublishImpactItem();
                rollbackImpact.setJobId(rollbackJob.getId());
                rollbackImpact.setPageType(targetImpact.getPageType());
                rollbackImpact.setSourceType(targetImpact.getSourceType());
                rollbackImpact.setSourceId(targetImpact.getSourceId());
                rollbackImpact.setObjectType(targetImpact.getObjectType());
                rollbackImpact.setObjectId(targetImpact.getObjectId());
                rollbackImpact.setPath(targetImpact.getPath());
                rollbackImpact.setAction("rollback");
                rollbackImpact.setSummary("Rollback " + Optional.ofNullable(targetImpact.getSummary()).orElse("impact"));
                publishImpactItemRepository.save(rollbackImpact);
            }

            for (PublishArtifact artifact : targetArtifacts) {
                Path outputPath = resolveOutputPath(targetJob.getSiteId(), artifact.getOutputPath());
                PublishArtifact rollbackArtifact = new PublishArtifact();
                rollbackArtifact.setJobId(rollbackJob.getId());
                rollbackArtifact.setArtifactType("rollback_restore");
                rollbackArtifact.setOutputPath(artifact.getOutputPath());
                rollbackArtifact.setVersion("rollback-" + rollbackJob.getId());

                if (artifact.getBackupPath() != null && !artifact.getBackupPath().isBlank()) {
                    Path backupPath = Paths.get(artifact.getBackupPath());
                    if (Files.exists(backupPath)) {
                        Files.createDirectories(outputPath.getParent());
                        Files.copy(backupPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
                        rollbackArtifact.setBackupPath(artifact.getBackupPath());
                        rollbackArtifact.setChecksum(sha256(Files.readAllBytes(outputPath)));
                        logs.add("Restored backup for " + artifact.getOutputPath());
                    } else {
                        Files.deleteIfExists(outputPath);
                        logs.add("Backup missing, deleted artifact " + artifact.getOutputPath());
                    }
                } else {
                    Files.deleteIfExists(outputPath);
                    logs.add("Removed newly created artifact " + artifact.getOutputPath());
                }
                publishArtifactRepository.save(rollbackArtifact);
            }

            applyContentStatusAfterRollback(targetJob, rollbackJob, reason);

            PublishRollbackRecord rollbackRecord = new PublishRollbackRecord();
            rollbackRecord.setRollbackJobId(rollbackJob.getId());
            rollbackRecord.setTargetJobId(targetJob.getId());
            rollbackRecord.setReason(reason);
            rollbackRecord.setOperatorName(currentOperatorName());
            publishRollbackRecordRepository.save(rollbackRecord);

            rollbackJob.setStatus("rollback_success");
            rollbackJob.setFinishedAt(LocalDateTime.now());
            rollbackJob.setFailureReason(null);
            rollbackJob.setResultSummary("Rollback completed for job #" + targetJob.getId());
            rollbackJob.setLogContent(String.join("\n", logs));
            return publishJobRepository.save(rollbackJob);
        } catch (Exception exception) {
            rollbackJob.setStatus("rollback_failed");
            rollbackJob.setFinishedAt(LocalDateTime.now());
            rollbackJob.setFailureReason(exception.getMessage());
            logs.add("Rollback failed: " + exception.getMessage());
            rollbackJob.setLogContent(String.join("\n", logs));
            publishJobRepository.save(rollbackJob);
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    private PublishRequest normalizeRequest(PublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publish request is required.");
        }
        if (request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        request.setUnitType(publishImpactCalculator.normalizeUnitType(request.getUnitType()));
        request.setMode(publishImpactCalculator.normalizeMode(request.getMode(), request.getUnitType()));
        if (request.getUnitIds() == null || request.getUnitIds().isEmpty()) {
            if ("site".equals(request.getUnitType())) {
                request.setUnitIds(List.of(request.getSiteId()));
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitIds is required.");
            }
        }
        return request;
    }

    private List<String> validate(PublishRequest request) {
        List<String> reasons = new ArrayList<>();
        Site site = siteRepository.findById(request.getSiteId()).orElse(null);
        if (site == null) {
            reasons.add("site does not exist");
            return reasons;
        }
        if (!"enabled".equalsIgnoreCase(site.getStatus())) {
            reasons.add("site is not enabled");
        }
        switch (request.getUnitType()) {
            case "content" -> validateContentRequest(request, reasons);
            case "category" -> validateCategoryRequest(request, reasons);
            case "template" -> validateTemplateRequest(request, reasons);
            case "site" -> {
            }
            default -> reasons.add("unsupported unit type");
        }
        return reasons;
    }

    private void validateContentRequest(PublishRequest request, List<String> reasons) {
        for (Long articleId : request.getUnitIds()) {
            Article article = articleRepository.findById(articleId).orElse(null);
            if (article == null) {
                reasons.add("content " + articleId + " does not exist");
                continue;
            }
            if (!Objects.equals(article.getSiteId(), request.getSiteId())) {
                reasons.add("content " + articleId + " does not belong to site");
            }
            if ("incremental".equals(request.getMode())) {
                ArticlePublishCheckResponse check = articleService.publishCheck(articleId);
                reasons.addAll(check.getReasons().stream().map(reason -> "content " + articleId + ": " + reason).toList());
            }
            if ("offline".equals(request.getMode()) && article.getStatus() != ArticleStatus.published) {
                reasons.add("content " + articleId + " must be published before offline");
            }
        }
    }

    private void validateCategoryRequest(PublishRequest request, List<String> reasons) {
        for (Long categoryId : request.getUnitIds()) {
            Category category = categoryRepository.findByIdAndSiteId(categoryId, request.getSiteId()).orElse(null);
            if (category == null) {
                reasons.add("category " + categoryId + " does not exist");
                continue;
            }
            if (!"enabled".equalsIgnoreCase(category.getStatus())) {
                reasons.add("category " + categoryId + " is not enabled");
            }
        }
    }

    private void validateTemplateRequest(PublishRequest request, List<String> reasons) {
        for (Long templateId : request.getUnitIds()) {
            var template = templateRepository.findByIdAndSiteId(templateId, request.getSiteId()).orElse(null);
            if (template == null) {
                reasons.add("template " + templateId + " does not exist");
                continue;
            }
            if (!"active".equalsIgnoreCase(template.getStatus())) {
                reasons.add("template " + templateId + " is not active");
            }
            if ("topic_page".equalsIgnoreCase(template.getType())) {
                reasons.add("template " + templateId + " is topic_page and not supported yet");
            }
        }
    }

    private void executeJob(PublishJob job, List<PublishImpactItem> impacts, List<String> logs) throws IOException {
        PublishSnapshot snapshot = readSnapshot(job.getSourceSnapshot());
        for (PublishImpactItem impact : impacts) {
            if ("delete".equalsIgnoreCase(impact.getAction())) {
                handleDeleteArtifact(job, impact, logs);
                continue;
            }
            Long templateId = resolveTemplateId(job.getSiteId(), impact);
            if (templateId == null) {
                if ("content-detail".equals(impact.getPageType())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "No active template for impact path: " + impact.getPath());
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
            Path outputPath = resolveOutputPath(job.getSiteId(), impact.getPath());
            Files.createDirectories(outputPath.getParent());
            String backupPath = backupIfExists(job, outputPath);
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

    private void applyContentStatusAfterSuccess(PublishJob job, PublishRequest request, List<String> logs) {
        if (!"content".equals(request.getUnitType())) {
            return;
        }
        for (Long articleId : request.getUnitIds()) {
            if ("incremental".equals(request.getMode())) {
                articleService.applyPublishSuccess(articleId, job.getId(), currentOperatorName());
                logs.add("Marked article as published: " + articleId);
            } else if ("offline".equals(request.getMode())) {
                articleService.applyOfflineSuccess(articleId, "Published through offline job", job.getId(), currentOperatorName());
                logs.add("Marked article as offline: " + articleId);
            }
        }
    }

    private void applyContentStatusAfterRollback(PublishJob targetJob, PublishJob rollbackJob, String reason) {
        PublishSnapshot snapshot = readSnapshot(targetJob.getSourceSnapshot());
        if (!"content".equals(snapshot.unitType)) {
            return;
        }
        for (Long articleId : snapshot.unitIds) {
            String previousStatus = snapshot.articleStatuses.get(String.valueOf(articleId));
            if ("approved".equals(previousStatus)) {
                articleService.rollbackToApproved(articleId, rollbackJob.getId(), currentOperatorName(), reason);
            } else if ("published".equals(previousStatus)) {
                articleService.rollbackToPublished(articleId, rollbackJob.getId(), currentOperatorName(), reason);
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
        Category category = categoryRepository.findByIdAndSiteId(categoryId, siteId).orElse(null);
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
        Category category = article.getPrimaryCategoryId() == null ? null : categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), siteId).orElse(null);
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

    private void handleDeleteArtifact(PublishJob job, PublishImpactItem impact, List<String> logs) throws IOException {
        Path outputPath = resolveOutputPath(job.getSiteId(), impact.getPath());
        String backupPath = backupIfExists(job, outputPath);
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

    private Path resolveSiteOutputRoot(Long siteId) {
        return Paths.get(publishStoragePath).resolve("site-" + siteId);
    }

    private Path resolveOutputPath(Long siteId, String logicalPath) {
        String sanitized = logicalPath == null || logicalPath.isBlank() ? "/index.html" : logicalPath;
        String relative = sanitized.startsWith("/") ? sanitized.substring(1) : sanitized;
        return resolveSiteOutputRoot(siteId).resolve(relative);
    }

    private String backupIfExists(PublishJob job, Path outputPath) throws IOException {
        if (!Files.exists(outputPath)) {
            return null;
        }
        Path backupRoot = resolveSiteOutputRoot(job.getSiteId()).resolve(".backups").resolve("job-" + job.getId());
        Path relative = resolveSiteOutputRoot(job.getSiteId()).relativize(outputPath);
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

    private String buildSourceSnapshot(PublishRequest request) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("siteId", request.getSiteId());
            root.put("unitType", request.getUnitType());
            root.put("unitIds", request.getUnitIds());
            root.put("mode", request.getMode());
            root.put("operatorComment", request.getOperatorComment());
            Map<String, String> articleStatuses = new LinkedHashMap<>();
            if ("content".equals(request.getUnitType())) {
                for (Long articleId : request.getUnitIds()) {
                    Article article = articleRepository.findById(articleId).orElse(null);
                    if (article != null && article.getStatus() != null) {
                        articleStatuses.put(String.valueOf(articleId), article.getStatus().value());
                    }
                }
            }
            root.put("articleStatuses", articleStatuses);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PublishRequest readRequestFromSnapshot(String sourceSnapshot) {
        PublishSnapshot snapshot = readSnapshot(sourceSnapshot);
        PublishRequest request = new PublishRequest();
        request.setSiteId(snapshot.siteId);
        request.setUnitType(snapshot.unitType);
        request.setUnitIds(snapshot.unitIds);
        request.setMode(snapshot.mode);
        request.setOperatorComment(snapshot.operatorComment);
        return request;
    }

    private PublishSnapshot readSnapshot(String sourceSnapshot) {
        try {
            JsonNode root = objectMapper.readTree(sourceSnapshot);
            PublishSnapshot snapshot = new PublishSnapshot();
            snapshot.siteId = root.path("siteId").isMissingNode() ? null : root.path("siteId").asLong();
            snapshot.unitType = root.path("unitType").asText(null);
            snapshot.mode = root.path("mode").asText(null);
            snapshot.operatorComment = root.path("operatorComment").asText(null);
            snapshot.unitIds = new ArrayList<>();
            if (root.path("unitIds").isArray()) {
                for (JsonNode node : root.path("unitIds")) {
                    snapshot.unitIds.add(node.asLong());
                }
            }
            snapshot.articleStatuses = new LinkedHashMap<>();
            JsonNode statusesNode = root.path("articleStatuses");
            if (statusesNode.isObject()) {
                statusesNode.fields().forEachRemaining(entry -> snapshot.articleStatuses.put(entry.getKey(), entry.getValue().asText()));
            }
            return snapshot;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid publish snapshot.", exception);
        }
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private PublishImpactResponse.Item toImpactView(PublishImpactItem item) {
        PublishImpactResponse.Item view = new PublishImpactResponse.Item();
        view.setPageType(item.getPageType());
        view.setSourceType(item.getSourceType());
        view.setSourceId(item.getSourceId());
        view.setObjectType(item.getObjectType());
        view.setObjectId(item.getObjectId());
        view.setPath(item.getPath());
        view.setAction(item.getAction());
        view.setSummary(item.getSummary());
        return view;
    }

    private String currentOperatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private static class PublishSnapshot {
        private Long siteId;
        private String unitType;
        private List<Long> unitIds;
        private String mode;
        private String operatorComment;
        private Map<String, String> articleStatuses;
    }
}
