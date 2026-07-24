package gov.cms.admin.service;

import gov.cms.admin.dto.DashboardDto;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.AuditLogRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private UserRepository userRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SiteAccessService siteAccessService;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        when(articleRepository.count()).thenReturn(100L);
        when(userRepository.count()).thenReturn(10L);
        when(siteRepository.count()).thenReturn(5L);
        when(auditLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
    }

    @Test
    void adminShouldSeeGlobalStats() {
        User admin = createUser("admin");
        when(siteAccessService.isAdmin()).thenReturn(true);

        DashboardDto dto = dashboardService.getStatsForUser(admin);

        assertThat(dto.getArticleCount()).isEqualTo(100L);
        assertThat(dto.getUserCount()).isEqualTo(10L);
        assertThat(dto.getSiteCount()).isEqualTo(5L);
    }

    private User createUser(String roleCode) {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        return user;
    }
}
