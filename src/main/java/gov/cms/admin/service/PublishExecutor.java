package gov.cms.admin.service;

import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishEnvironment;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PublishExecutor {

  private final PublishJobRepository publishJobRepository;
  private final PublishArtifactRepository publishArtifactRepository;
  private final PortalRenderService portalRenderService;
  private final RenderContextAssembler renderContextAssembler;

  public PublishExecutor(PublishJobRepository publishJobRepository,
                         PublishArtifactRepository publishArtifactRepository,
                         PortalRenderService portalRenderService,
                         RenderContextAssembler renderContextAssembler) {
    this.publishJobRepository = publishJobRepository;
    this.publishArtifactRepository = publishArtifactRepository;
    this.portalRenderService = portalRenderService;
    this.renderContextAssembler = renderContextAssembler;
  }

  @Transactional
  public void execute(Long jobId, String environmentName) {
    PublishJob job = publishJobRepository.findById(jobId)
        .orElseThrow(() -> new IllegalArgumentException("Publish job not found: " + jobId));

    String targetEnv = environmentName != null ? environmentName : job.getEnvironment();
    if (targetEnv == null) {
      targetEnv = PublishEnvironment.PRODUCTION.name().toLowerCase();
    }

    String renderingState = targetEnv.equalsIgnoreCase("staging")
        ? "staging_rendering" : "production_rendering";
    PublishStateMachine.requireTransition(job.getStatus(), renderingState);
    job.setStatus(renderingState);
    job.setStartedAt(LocalDateTime.now());
    publishJobRepository.save(job);

    try {
      Path outputRoot = resolveOutputRoot(job.getSiteId(), targetEnv);
      // Actual rendering logic moved from PublishService will be invoked here
      // portalRenderService.render(...) etc.

      job.setStatus(targetEnv.equalsIgnoreCase("staging") ? "staging_ready" : "published");
      job.setFinishedAt(LocalDateTime.now());
      if (targetEnv.equalsIgnoreCase("staging")) {
        job.setPreviewToken(UUID.randomUUID().toString().replace("-", ""));
      }
      publishJobRepository.save(job);
    } catch (Exception e) {
      job.setStatus("failed");
      job.setFailureReason(e.getMessage());
      job.setFinishedAt(LocalDateTime.now());
      publishJobRepository.save(job);
      throw new RuntimeException("Publish execution failed for job " + jobId, e);
    }
  }

  private Path resolveOutputRoot(Long siteId, String environment) {
    return Paths.get("./storage/publish", String.valueOf(siteId), environment.toLowerCase());
  }
}
