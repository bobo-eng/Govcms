package gov.cms.admin.controller;

import gov.cms.admin.dto.PublishCheckResponse;
import gov.cms.admin.dto.PublishImpactResponse;
import gov.cms.admin.dto.PublishRequest;
import gov.cms.admin.dto.PublishRollbackRequest;
import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.entity.PublishRollbackRecord;
import gov.cms.admin.service.PublishService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/publish")
@CrossOrigin(origins = "*")
public class PublishController {

    private final PublishService publishService;

    public PublishController(PublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<PublishCheckResponse> check(@RequestBody PublishRequest request) {
        return ResponseEntity.ok(publishService.check(request));
    }

    @PostMapping("/impact")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<PublishImpactResponse> impact(@RequestBody PublishRequest request) {
        return ResponseEntity.ok(publishService.impact(request));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> createAndQueue(@RequestBody PublishRequest request,
                                                     @RequestParam(required = false, defaultValue = "production") String environment,
                                                     @RequestParam(required = false) java.time.LocalDateTime scheduledAt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publishService.createAndQueue(request, environment, scheduledAt));
    }

    @PostMapping("/jobs/{id}/approve")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> approveJob(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.approveJob(id));
    }

    @PostMapping("/jobs/{id}/reject")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> rejectJob(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.rejectJob(id));
    }

    @GetMapping("/jobs/{id}/preview")
    public ResponseEntity<String> previewToken(@PathVariable Long id) {
        PublishJob job = publishService.getJob(id);
        if (job == null || job.getPreviewToken() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job.getPreviewToken());
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<List<PublishJob>> getJobs(@RequestParam(required = false) Long siteId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String mode,
                                                    @RequestParam(required = false) String unitType) {
        return ResponseEntity.ok(publishService.listJobs(siteId, status, mode, unitType));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<PublishJob> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.getJob(id));
    }

    @GetMapping("/jobs/{id}/impacts")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<List<PublishImpactItem>> getImpacts(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.getImpacts(id));
    }

    @GetMapping("/jobs/{id}/artifacts")
    @PreAuthorize("hasAuthority('publish:center:artifact:view')")
    public ResponseEntity<List<PublishArtifact>> getArtifacts(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.getArtifacts(id));
    }

    @GetMapping("/jobs/{id}/logs")
    @PreAuthorize("hasAuthority('publish:center:log:view')")
    public ResponseEntity<List<String>> getLogs(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.getLogs(id));
    }

    @GetMapping("/jobs/{id}/audits")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<List<AuditLog>> getAudits(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.getAuditLogs(id));
    }

    @GetMapping("/jobs/{id}/rollback-records")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<List<PublishRollbackRecord>> getRollbackRecords(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.getRollbackRecords(id));
    }

    @PostMapping("/jobs/{id}/retry")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> retry(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.retry(id));
    }

    @PostMapping("/jobs/{id}/rollback")
    @PreAuthorize("hasAuthority('publish:center:rollback')")
    public ResponseEntity<PublishJob> rollback(@PathVariable Long id, @RequestBody(required = false) PublishRollbackRequest request) {
        return ResponseEntity.ok(publishService.rollback(id, request));
    }
}
