package gov.cms.admin.controller;

import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<Page<AuditLog>> getLogs(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String operatorName,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditLogService.list(siteId, actionType, result, operatorName, pageable));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<List<AuditLog>> getJobLogs(@PathVariable Long jobId) {
        return ResponseEntity.ok(auditLogService.listByJobId(jobId));
    }
}
