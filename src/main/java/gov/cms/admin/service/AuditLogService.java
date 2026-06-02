package gov.cms.admin.service;

import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final SiteAccessService siteAccessService;

    public AuditLogService(AuditLogRepository auditLogRepository, SiteAccessService siteAccessService) {
        this.auditLogRepository = auditLogRepository;
        this.siteAccessService = siteAccessService;
    }

    public AuditLog record(String actionType,
                           String objectType,
                           Long objectId,
                           Long siteId,
                           String result,
                           String summary,
                           String failureReason,
                           Long relatedJobId) {
        AuditLog log = new AuditLog();
        log.setActionType(actionType);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setSiteId(siteId);
        log.setResult(result);
        log.setSummary(summary);
        log.setFailureReason(failureReason);
        log.setRelatedJobId(relatedJobId);
        log.setOperatorName(resolveOperatorName());
        return auditLogRepository.save(log);
    }

    public List<AuditLog> list(Long siteId, String actionType, String result, String operatorName) {
        Long accessibleSiteId = siteAccessService.isScopedSiteAdmin() ? siteAccessService.resolveAccessibleSiteId(siteId) : siteId;
        return auditLogRepository.search(accessibleSiteId, actionType, result, operatorName);
    }

    public List<AuditLog> listByJobId(Long jobId) {
        return auditLogRepository.findByRelatedJobIdOrderByCreatedAtDescIdDesc(jobId);
    }

    private String resolveOperatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }
}
