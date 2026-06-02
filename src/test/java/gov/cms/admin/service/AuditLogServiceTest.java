package gov.cms.admin.service;

import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SiteAccessService siteAccessService;

    @InjectMocks private AuditLogService auditLogService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordUsesCurrentOperator() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "pwd"));
        AuditLog saved = new AuditLog();
        saved.setId(1L);
        saved.setOperatorName("admin");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog result = auditLogService.record("publish_execute", "publish_job", 10L, 1L, "success", "done", null, 100L);

        assertEquals("admin", result.getOperatorName());
        assertEquals("publish_execute", result.getActionType());
    }

    @Test
    void listByJobIdReturnsRepositoryResult() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        when(auditLogRepository.findByRelatedJobIdOrderByCreatedAtDescIdDesc(100L)).thenReturn(List.of(log));

        List<AuditLog> result = auditLogService.listByJobId(100L);

        assertEquals(1, result.size());
    }
}
