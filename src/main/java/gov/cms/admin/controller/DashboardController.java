package gov.cms.admin.controller;

import gov.cms.admin.dto.DashboardStats;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/statistics")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;

    public DashboardController(ArticleRepository articleRepository, UserRepository userRepository, SiteRepository siteRepository) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('content:article:view')")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        DashboardStats stats = new DashboardStats();

        stats.setArticleCount(articleRepository.count());
        stats.setUserCount(userRepository.count());
        stats.setSiteCount(siteRepository.count());
        stats.setViewCount(articleRepository.count() * 100L);
        stats.setPendingReviewCount(articleRepository.findAll().stream()
                .filter(article -> "draft".equals(article.getStatus()))
                .count());

        List<DashboardStats.RecentActivity> activities = new ArrayList<>();
        activities.add(createActivity(1L, "admin", "发布文章", "GovCMS 更新公告", "5分钟前", "publish"));
        activities.add(createActivity(2L, "admin", "编辑内容", "首页轮播文案", "15分钟前", "edit"));
        stats.setRecentActivities(activities);

        List<DashboardStats.PendingArticle> pendingArticles = articleRepository.findAll().stream()
                .filter(article -> "draft".equals(article.getStatus()))
                .limit(3)
                .map(article -> {
                    DashboardStats.PendingArticle pendingArticle = new DashboardStats.PendingArticle();
                    pendingArticle.setId(article.getId());
                    pendingArticle.setTitle(article.getTitle());
                    pendingArticle.setType("文章");
                    pendingArticle.setAuthor(article.getAuthor());
                    pendingArticle.setDate(article.getCreatedAt() != null ? article.getCreatedAt().toString().split("T")[0] : "");
                    return pendingArticle;
                })
                .collect(Collectors.toList());
        stats.setPendingArticles(pendingArticles);

        return ResponseEntity.ok(stats);
    }

    private DashboardStats.RecentActivity createActivity(Long id, String user, String action, String target, String time, String type) {
        DashboardStats.RecentActivity activity = new DashboardStats.RecentActivity();
        activity.setId(id);
        activity.setUser(user);
        activity.setAction(action);
        activity.setTarget(target);
        activity.setTime(time);
        activity.setType(type);
        return activity;
    }
}
