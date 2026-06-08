package gov.cms.admin.service;

import gov.cms.admin.dto.DashboardDto;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.AuditLogRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final AuditLogRepository auditLogRepository;
    private final SiteAccessService siteAccessService;

    public DashboardService(ArticleRepository articleRepository,
                            UserRepository userRepository,
                            SiteRepository siteRepository,
                            AuditLogRepository auditLogRepository,
                            SiteAccessService siteAccessService) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.auditLogRepository = auditLogRepository;
        this.siteAccessService = siteAccessService;
    }

    public DashboardDto getStatsForUser(User user) {
        if (siteAccessService.isAdmin()) {
            return buildAdminStats();
        }
        if (siteAccessService.isSiteAdmin()) {
            return buildSiteAdminStats(user);
        }
        if (isEditor(user)) {
            return buildEditorStats(user);
        }
        if (isReviewer(user)) {
            return buildReviewerStats(user);
        }
        if (isPublisher(user)) {
            return buildPublisherStats();
        }
        return buildViewerStats(user);
    }

    private DashboardDto buildAdminStats() {
        DashboardDto dto = new DashboardDto();
        dto.setArticleCount(articleRepository.count());
        dto.setUserCount(userRepository.count());
        dto.setSiteCount(siteRepository.count());
        dto.setPendingReviewCount(articleRepository.countByStatus(ArticleStatus.pending_review));
        dto.setRecentActivities(mapRecentActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        dto.setPendingArticles(mapPendingArticles(articleRepository.findTop3ByStatusOrderByCreatedAtDesc(ArticleStatus.pending_review)));
        dto.setShowHealthPanel(true);
        return dto;
    }

    private DashboardDto buildSiteAdminStats(User user) {
        DashboardDto dto = new DashboardDto();
        Long siteId = user.getManagedSiteId();
        dto.setArticleCount(siteId != null ? articleRepository.countBySiteId(siteId) : 0L);
        dto.setSiteCount(1L);
        dto.setPendingReviewCount(siteId != null ? articleRepository.countBySiteIdAndStatus(siteId, ArticleStatus.pending_review) : 0L);
        dto.setRecentActivities(mapRecentActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        dto.setPendingArticles(mapPendingArticles(siteId != null ? articleRepository.findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(siteId, ArticleStatus.pending_review).stream().limit(3).collect(Collectors.toList()) : List.of()));
        dto.setShowHealthPanel(true);
        return dto;
    }

    private DashboardDto buildEditorStats(User user) {
        DashboardDto dto = new DashboardDto();
        dto.setMyDraftCount(0L);
        dto.setRecentActivities(mapRecentActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        return dto;
    }

    private DashboardDto buildReviewerStats(User user) {
        DashboardDto dto = new DashboardDto();
        Long siteId = user.getManagedSiteId();
        dto.setPendingReviewCount(siteId != null ? articleRepository.countBySiteIdAndStatus(siteId, ArticleStatus.pending_review) : articleRepository.countByStatus(ArticleStatus.pending_review));
        dto.setPendingArticles(mapPendingArticles(siteId != null ? articleRepository.findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(siteId, ArticleStatus.pending_review).stream().limit(3).collect(Collectors.toList()) : articleRepository.findTop3ByStatusOrderByCreatedAtDesc(ArticleStatus.pending_review)));
        dto.setRecentActivities(mapRecentActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        return dto;
    }

    private DashboardDto buildPublisherStats() {
        DashboardDto dto = new DashboardDto();
        dto.setPublishQueueCount(0L);
        dto.setFailedTaskCount(0L);
        dto.setRecentActivities(mapRecentActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        return dto;
    }

    private DashboardDto buildViewerStats(User user) {
        DashboardDto dto = new DashboardDto();
        Long siteId = user.getManagedSiteId();
        dto.setArticleCount(siteId != null ? articleRepository.countBySiteId(siteId) : articleRepository.count());
        dto.setSiteCount(siteId != null ? 1L : siteRepository.count());
        return dto;
    }

    private boolean isEditor(User user) {
        return hasRole(user, "editor");
    }

    private boolean isReviewer(User user) {
        return hasRole(user, "reviewer");
    }

    private boolean isPublisher(User user) {
        return hasRole(user, "publisher");
    }

    private boolean hasRole(User user, String roleCode) {
        if (user.getRoles() == null) {
            return false;
        }
        for (Role role : user.getRoles()) {
            if (roleCode.equals(role.getCode())) {
                return true;
            }
        }
        return false;
    }

    private List<DashboardDto.RecentActivity> mapRecentActivities(List<AuditLog> logs) {
        return logs.stream().map(log -> {
            DashboardDto.RecentActivity activity = new DashboardDto.RecentActivity();
            activity.setId(log.getId());
            activity.setUser(log.getOperatorName());
            activity.setAction(log.getActionType());
            activity.setTarget(log.getSummary());
            activity.setTime(log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
            activity.setType(log.getResult());
            return activity;
        }).collect(Collectors.toList());
    }

    private List<DashboardDto.PendingArticle> mapPendingArticles(List<Article> articles) {
        return articles.stream().map(article -> {
            DashboardDto.PendingArticle pending = new DashboardDto.PendingArticle();
            pending.setId(article.getId());
            pending.setTitle(article.getTitle());
            pending.setType("文章");
            pending.setAuthor(article.getAuthor());
            pending.setDate(article.getCreatedAt() != null ? article.getCreatedAt().toString().split("T")[0] : "");
            return pending;
        }).collect(Collectors.toList());
    }
}
