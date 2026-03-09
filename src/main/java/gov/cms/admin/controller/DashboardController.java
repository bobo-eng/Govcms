package gov.cms.admin.controller;

import gov.cms.admin.dto.DashboardStats;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.http.ResponseEntity;
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

    public DashboardController(ArticleRepository articleRepository, UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        DashboardStats stats = new DashboardStats();

        // Basic counts
        stats.setArticleCount(articleRepository.count());
        stats.setUserCount(userRepository.count());
        stats.setSiteCount(1L); // Simplified - single site for MVP
        stats.setViewCount(articleRepository.count() * 100L); // Mock view count
        stats.setPendingReviewCount(articleRepository.findAll().stream()
                .filter(a -> "draft".equals(a.getStatus()))
                .count());

        // Recent activities - mock for MVP
        List<DashboardStats.RecentActivity> activities = new ArrayList<>();
        activities.add(createActivity(1L, "admin", "发布了文章", "关于政务公开的通知", "5分钟前", "publish"));
        activities.add(createActivity(2L, "admin", "更新了页面", "关于我们", "15分钟前", "edit"));
        stats.setRecentActivities(activities);

        // Pending articles
        List<DashboardStats.PendingArticle> pending = articleRepository.findAll().stream()
                .filter(a -> "draft".equals(a.getStatus()))
                .limit(3)
                .map(a -> {
                    DashboardStats.PendingArticle p = new DashboardStats.PendingArticle();
                    p.setId(a.getId());
                    p.setTitle(a.getTitle());
                    p.setType("文章");
                    p.setAuthor(a.getAuthor());
                    p.setDate(a.getCreatedAt() != null ? a.getCreatedAt().toString().split("T")[0] : "");
                    return p;
                })
                .collect(Collectors.toList());
        stats.setPendingArticles(pending);

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
