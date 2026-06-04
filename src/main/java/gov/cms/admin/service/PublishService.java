package gov.cms.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.ArticlePublishCheckResponse;
import gov.cms.admin.dto.PublishCheckResponse;
import gov.cms.admin.dto.PublishImpactResponse;
import gov.cms.admin.dto.PublishRequest;
import gov.cms.admin.dto.PublishRollbackRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.entity.PublishRollbackRecord;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishImpactItemRepository;
import gov.cms.admin.repository.PublishJobRepository;
import gov.cms.admin.repository.PublishRollbackRecordRepository;
import gov.cms.admin.repository.NavigationItemRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TopicContentItemRepository;
import gov.cms.admin.repository.TopicRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.scheduler.PublishQuartzJob;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
import java.util.Set;
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
    private final NavigationItemRepository navigationItemRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final TopicRepository topicRepository;
    private final TemplateRepository templateRepository;
    private final TemplateBindingRepository templateBindingRepository;
    private final ArticleService articleService;
    private final MediaReferenceService mediaReferenceService;
    private final RenderContextAssembler renderContextAssembler;
    private final PortalRenderService portalRenderService;
    private final ObjectMapper objectMapper;
    private final SiteAccessService siteAccessService;
    private final AuditLogService auditLogService;
    private final SearchIndexService searchIndexService;
    private final Scheduler scheduler;
    private final PublishExecutor publishExecutor;
    private final String publishStoragePath;

    public PublishService(PublishJobRepository publishJobRepository,
                          PublishImpactItemRepository publishImpactItemRepository,
                          PublishArtifactRepository publishArtifactRepository,
                          PublishRollbackRecordRepository publishRollbackRecordRepository,
                          PublishImpactCalculator publishImpactCalculator,
                          ArticleRepository articleRepository,
                          CategoryRepository categoryRepository,
                          SiteRepository siteRepository,
                          NavigationItemRepository navigationItemRepository,
                          TopicContentItemRepository topicContentItemRepository,
                          TopicRepository topicRepository,
                          TemplateRepository templateRepository,
                          TemplateBindingRepository templateBindingRepository,
                          ArticleService articleService,
                          MediaReferenceService mediaReferenceService,
                          RenderContextAssembler renderContextAssembler,
                          PortalRenderService portalRenderService,
                          ObjectMapper objectMapper,
                          SiteAccessService siteAccessService,
                          AuditLogService auditLogService,
                          SearchIndexService searchIndexService,
                          Scheduler scheduler,
                          PublishExecutor publishExecutor,
                          @Value("${app.publish.storage-path:./storage/publish}") String publishStoragePath) {
        this.publishJobRepository = publishJobRepository;
        this.publishImpactItemRepository = publishImpactItemRepository;
        this.publishArtifactRepository = publishArtifactRepository;
        this.publishRollbackRecordRepository = publishRollbackRecordRepository;
        this.publishImpactCalculator = publishImpactCalculator;
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
        this.navigationItemRepository = navigationItemRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.topicRepository = topicRepository;
        this.templateRepository = templateRepository;
        this.templateBindingRepository = templateBindingRepository;
        this.articleService = articleService;
        this.renderContextAssembler = renderContextAssembler;
        this.mediaReferenceService = mediaReferenceService;
        this.portalRenderService = portalRenderService;
        this.objectMapper = objectMapper;
        this.siteAccessService = siteAccessService;
        this.auditLogService = auditLogService;
        this.searchIndexService = searchIndexService;
        this.scheduler = scheduler;
        this.publishExecutor = publishExecutor;
        this.publishStoragePath = publishStoragePath;
    }

    public PublishCheckResponse check(PublishRequest request) {
        PublishRequest normalized = normalizeRequest(request);
        PublishCheckResponse response = new PublishCheckResponse();
        response.setSiteId(normalized.getSiteId());
        response.setUnitType(normalized.getUnitType());
        response.setMode(normalized.getMode());
        List<String> reasons = validate(normalized);
        PublishImpactCalculator.ImpactPlan plan = publishImpactCalculator.calculate(normalized);
        List<String> mediaWarnings = collectMediaWarnings(normalized);
        response.setWarnings(new ArrayList<>(plan.getWarnings()));
        response.getWarnings().addAll(mediaWarnings);
        response.setImpactCount(plan.getItems().size());
        response.setReasons(reasons);
        response.setPublishable(reasons.isEmpty() && !plan.getItems().isEmpty());
        if (plan.getItems().isEmpty()) {
            response.getReasons().add("no publish impacts generated");
            response.setPublishable(false);
        }
        auditLogService.record(
                "publish_check",
                normalized.getUnitType(),
                normalized.getUnitIds().isEmpty() ? null : normalized.getUnitIds().get(0),
                normalized.getSiteId(),
                response.isPublishable() ? "success" : "blocked",
                "Publish precheck executed",
                response.isPublishable() ? null : String.join("; ", response.getReasons()),
                null
        );
        return response;
    }

    public PublishImpactResponse impact(PublishRequest request) {
        PublishRequest normalized = normalizeRequest(request);
        PublishImpactCalculator.ImpactPlan plan = publishImpactCalculator.calculate(normalized);
        List<String> mediaWarnings = collectMediaWarnings(normalized);
        PublishImpactResponse response = new PublishImpactResponse();
        response.setSiteId(normalized.getSiteId());
        response.setUnitType(normalized.getUnitType());
        response.setMode(normalized.getMode());
        response.setWarnings(new ArrayList<>(plan.getWarnings()));
        response.getWarnings().addAll(mediaWarnings);
        response.setTotalItems(plan.getItems().size());
        response.setItems(plan.getItems().stream().map(this::toImpactView).collect(Collectors.toList()));
        auditLogService.record(
                "publish_impact",
                normalized.getUnitType(),
                normalized.getUnitIds().isEmpty() ? null : normalized.getUnitIds().get(0),
                normalized.getSiteId(),
                "success",
                "Publish impact calculated",
                null,
                null
        );
        return response;
    }

    @Transactional
    public PublishJob createAndQueue(PublishRequest request, String environment, LocalDateTime scheduledAt) {
        PublishRequest normalized = normalizeRequest(request);
        PublishCheckResponse check = check(normalized);
        if (!check.isPublishable()) {
            auditLogService.record("publish_execute", normalized.getUnitType(), normalized.getUnitIds().isEmpty() ? null : normalized.getUnitIds().get(0), normalized.getSiteId(), "blocked", "Publish execution blocked", String.join("; ", check.getReasons()), null);
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("; ", check.getReasons()));
        }

        String targetEnv = environment != null ? environment.toLowerCase() : "production";
        if (!Set.of("staging", "production").contains(targetEnv)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid environment: " + environment);
        }
        PublishJob job = new PublishJob();
        job.setSiteId(normalized.getSiteId());
        job.setUnitType(normalized.getUnitType());
        job.setUnitIds(joinIds(normalized.getUnitIds()));
        job.setMode(normalized.getMode());
        job.setStatus("created");
        job.setEnvironment(targetEnv);
        job.setApprovalStatus("pending");
        job.setScheduledAt(scheduledAt);
        job.setOperatorName(currentOperatorName());
        job.setOutputRoot(Paths.get(publishStoragePath, String.valueOf(normalized.getSiteId()), targetEnv).toString().replace('\\', '/'));
        job.setSourceSnapshot(buildSourceSnapshot(normalized));
        job.setResultSummary(normalized.getOperatorComment());
        job = publishJobRepository.save(job);

        PublishImpactCalculator.ImpactPlan impactPlan = publishImpactCalculator.calculate(normalized);
        for (PublishImpactItem item : impactPlan.getItems()) {
            item.setJobId(job.getId());
        }
        publishImpactItemRepository.saveAll(impactPlan.getItems());

        schedulePublishTrigger(job);

        job.setStatus("queued");
        PublishJob saved = publishJobRepository.save(job);
        auditLogService.record("publish_execute", saved.getUnitType(), normalized.getUnitIds().isEmpty() ? null : normalized.getUnitIds().get(0), saved.getSiteId(), "success", "Publish job queued: #" + saved.getId(), null, saved.getId());
        return saved;
    }

    private void schedulePublishTrigger(PublishJob job) {
        try {
            JobDataMap jobData = new JobDataMap();
            jobData.put(PublishQuartzJob.JOB_DATA_PUBLISH_JOB_ID, job.getId());
            jobData.put(PublishQuartzJob.JOB_DATA_ENVIRONMENT, job.getEnvironment());

            JobDetail jobDetail = JobBuilder.newJob(PublishQuartzJob.class)
                    .withIdentity("publishJob-" + job.getId(), "publish")
                    .usingJobData(jobData)
                    .build();

            TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                    .withIdentity("publishTrigger-" + job.getId(), "publish")
                    .forJob(jobDetail);

            if (job.getScheduledAt() != null) {
                triggerBuilder.startAt(java.util.Date.from(job.getScheduledAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            } else {
                triggerBuilder.startNow();
            }

            Trigger trigger = triggerBuilder.build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule publish job " + job.getId(), e);
        }
    }

    @Transactional
    public PublishJob approveJob(Long jobId) {
        PublishJob job = publishJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!"staging_ready".equals(job.getStatus())) {
            throw new IllegalStateException("Only staging_ready jobs can be approved");
        }
        PublishStateMachine.requireTransition(job.getStatus(), "approved");
        job.setStatus("approved");
        job.setApprovalStatus("approved");
        publishJobRepository.save(job);

        schedulePublishTrigger(job);
        return job;
    }

    @Transactional
    public PublishJob rejectJob(Long jobId) {
        PublishJob job = publishJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!"staging_ready".equals(job.getStatus())) {
            throw new IllegalStateException("Only staging_ready jobs can be rejected");
        }
        job.setStatus("rejected");
        job.setApprovalStatus("rejected");
        return publishJobRepository.save(job);
    }

    public List<PublishJob> listJobs(Long siteId, String status, String mode, String unitType) {
        Long accessibleSiteId = siteAccessService.isScopedSiteAdmin() ? siteAccessService.getCurrentManagedSiteId() : siteId;
        return publishJobRepository.findAll().stream()
                .filter(job -> accessibleSiteId == null || Objects.equals(job.getSiteId(), accessibleSiteId))
                .filter(job -> status == null || status.isBlank() || status.equalsIgnoreCase(job.getStatus()))
                .filter(job -> mode == null || mode.isBlank() || mode.equalsIgnoreCase(job.getMode()))
                .filter(job -> unitType == null || unitType.isBlank() || unitType.equalsIgnoreCase(job.getUnitType()))
                .sorted((left, right) -> Optional.ofNullable(right.getCreatedAt()).orElse(LocalDateTime.MIN)
                        .compareTo(Optional.ofNullable(left.getCreatedAt()).orElse(LocalDateTime.MIN)))
                .toList();
    }

    public PublishJob getJob(Long id) {
        PublishJob job = publishJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publish job not found."));
        if (siteAccessService.isScopedSiteAdmin()) {
            siteAccessService.assertAccessibleSite(job.getSiteId());
        }
        return job;
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
            auditLogService.record("publish_retry", "publish_job", jobId, job.getSiteId(), "blocked", "Retry blocked", "Only failed jobs can be retried.", jobId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can be retried.");
        }
        PublishRequest request = readRequestFromSnapshot(job.getSourceSnapshot());
        PublishJob retried = createAndQueue(request, job.getEnvironment(), null);
        auditLogService.record("publish_retry", "publish_job", jobId, job.getSiteId(), "success", "Retry created new job #" + retried.getId(), null, retried.getId());
        return retried;
    }

    @Transactional
    public PublishJob rollback(Long jobId, PublishRollbackRequest request) {
        PublishJob targetJob = getJob(jobId);
        if (!("success".equalsIgnoreCase(targetJob.getStatus()) || "rollback_success".equalsIgnoreCase(targetJob.getStatus()) || "published".equalsIgnoreCase(targetJob.getStatus()))) {
            auditLogService.record("publish_rollback", "publish_job", jobId, targetJob.getSiteId(), "blocked", "Rollback blocked", "Only successful jobs can be rolled back.", jobId);
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
                Path outputPath;
                if (targetJob.getOutputRoot() != null && !targetJob.getOutputRoot().isBlank()) {
                    String sanitized = artifact.getOutputPath() == null || artifact.getOutputPath().isBlank() ? "/index.html" : artifact.getOutputPath();
                    String relative = sanitized.startsWith("/") ? sanitized.substring(1) : sanitized;
                    outputPath = Paths.get(targetJob.getOutputRoot()).resolve(relative);
                } else {
                    outputPath = resolveOutputPath(targetJob.getSiteId(), artifact.getOutputPath());
                }
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
            syncSearchIndexAfterRollback(targetJob, rollbackJob, logs);

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
            PublishJob saved = publishJobRepository.save(rollbackJob);
            auditLogService.record("publish_rollback", "publish_job", targetJob.getId(), targetJob.getSiteId(), "success", saved.getResultSummary(), null, saved.getId());
            return saved;
        } catch (Exception exception) {
            rollbackJob.setStatus("rollback_failed");
            rollbackJob.setFinishedAt(LocalDateTime.now());
            rollbackJob.setFailureReason(exception.getMessage());
            logs.add("Rollback failed: " + exception.getMessage());
            rollbackJob.setLogContent(String.join("\n", logs));
            publishJobRepository.save(rollbackJob);
            auditLogService.record("publish_rollback", "publish_job", targetJob.getId(), targetJob.getSiteId(), exception instanceof ResponseStatusException ? "blocked" : "failed", "Rollback failed", exception.getMessage(), rollbackJob.getId());
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
            if (siteAccessService.isScopedSiteAdmin()) {
                request.setSiteId(siteAccessService.resolveAccessibleSiteId(null));
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
            }
        } else if (siteAccessService.isScopedSiteAdmin()) {
            request.setSiteId(siteAccessService.resolveAccessibleSiteId(request.getSiteId()));
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
            case "navigation" -> validateNavigationRequest(request, reasons);
            case "topic" -> validateTopicRequest(request, reasons);
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

    private void validateNavigationRequest(PublishRequest request, List<String> reasons) {
        for (Long navigationId : request.getUnitIds()) {
            var navigation = navigationItemRepository.findByIdAndSiteId(navigationId, request.getSiteId()).orElse(null);
            if (navigation == null) {
                reasons.add("navigation " + navigationId + " does not exist");
                continue;
            }
            if (!"enabled".equalsIgnoreCase(navigation.getStatus())) {
                reasons.add("navigation " + navigationId + " is not enabled");
            }
        }
    }

    private void validateTopicRequest(PublishRequest request, List<String> reasons) {
        for (Long topicId : request.getUnitIds()) {
            Topic topic = topicRepository.findByIdAndSiteId(topicId, request.getSiteId()).orElse(null);
            if (topic == null) {
                reasons.add("topic " + topicId + " does not exist");
                continue;
            }
            if (!"active".equalsIgnoreCase(topic.getStatus())) {
                reasons.add("topic " + topicId + " is not active");
            }
            if (topic.getTemplateId() == null) {
                reasons.add("topic " + topicId + " has no template bound");
                continue;
            }
            var template = templateRepository.findByIdAndSiteId(topic.getTemplateId(), request.getSiteId()).orElse(null);
            if (template == null) {
                reasons.add("topic " + topicId + " template does not exist");
                continue;
            }
            if (!"active".equalsIgnoreCase(template.getStatus())) {
                reasons.add("topic " + topicId + " template is not active");
            }
            if (!"topic_page".equalsIgnoreCase(template.getType())) {
                reasons.add("topic " + topicId + " template is not topic_page");
            }
            if (countPublishableTopicArticles(topic) < 1) {
                reasons.add("topic " + topicId + " has no publishable content");
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

    private void syncSearchIndexAfterRollback(PublishJob targetJob, PublishJob rollbackJob, List<String> logs) {
        PublishSnapshot snapshot = readSnapshot(targetJob.getSourceSnapshot());
        if ("content".equals(snapshot.unitType)) {
            for (Long articleId : snapshot.unitIds) {
                String previousStatus = snapshot.articleStatuses.get(String.valueOf(articleId));
                if ("published".equals(previousStatus)) {
                    searchIndexService.syncContentEntry(articleId);
                    logs.add("Restored content search index: " + articleId);
                } else {
                    searchIndexService.removeContentEntry(targetJob.getSiteId(), articleId);
                    logs.add("Removed content search index after rollback: " + articleId);
                }
            }
        } else if (snapshot.siteId != null) {
            searchIndexService.rebuildSiteIndex(snapshot.siteId);
            logs.add("Rebuilt site search index after rollback: site-" + snapshot.siteId);
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

    private int countPublishableTopicArticles(Topic topic) {
        if (topic == null) {
            return 0;
        }
        int limit = topic.getRuleLimit() == null ? 10 : Math.max(1, Math.min(50, topic.getRuleLimit()));
        if ("rule_based".equalsIgnoreCase(topic.getAggregationMode())) {
            if (topic.getRuleCategoryId() != null) {
                return articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(topic.getSiteId(), topic.getRuleCategoryId(), ArticleStatus.published, org.springframework.data.domain.PageRequest.of(0, limit)).size();
            }
            return articleRepository.searchArticles(null, null, ArticleStatus.published, topic.getSiteId(), null, org.springframework.data.domain.PageRequest.of(0, limit)).getContent().size();
        }
        List<Long> articleIds = topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(topic.getId()).stream().map(item -> item.getArticleId()).toList();
        return (int) articleRepository.findAllById(articleIds).stream().filter(article -> Objects.equals(article.getSiteId(), topic.getSiteId())).filter(article -> article.getStatus() == ArticleStatus.published).count();
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


    public List<AuditLog> getAuditLogs(Long jobId) {
        getJob(jobId);
        return auditLogService.listByJobId(jobId);
    }

    public List<PublishRollbackRecord> getRollbackRecords(Long jobId) {
        getJob(jobId);
        return publishRollbackRecordRepository.findByTargetJobIdOrRollbackJobIdOrderByCreatedAtDesc(jobId, jobId);
    }

    public PublishArtifact getArtifact(Long id) {
        return publishArtifactRepository.findById(id).orElse(null);
    }

    public String resolveArtifactPath(PublishArtifact artifact) {
        return Paths.get(publishStoragePath, artifact.getOutputPath()).toString();
    }

    private List<String> collectMediaWarnings(PublishRequest request) {
        List<String> warnings = new ArrayList<>();
        switch (request.getUnitType()) {
            case "content" -> {
                for (Long articleId : request.getUnitIds()) {
                    Article article = articleRepository.findById(articleId).orElse(null);
                    if (article != null) {
                        warnings.addAll(mediaReferenceService.collectMissingMediaWarningsForArticle(article));
                    }
                }
            }
            case "topic" -> {
                for (Long topicId : request.getUnitIds()) {
                    Topic topic = topicRepository.findByIdAndSiteId(topicId, request.getSiteId()).orElse(null);
                    if (topic != null) {
                        warnings.addAll(mediaReferenceService.collectMissingMediaWarningsForTopic(topic));
                    }
                }
            }
            default -> {
            }
        }
        return warnings;
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















