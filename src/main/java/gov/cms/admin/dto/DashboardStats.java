package gov.cms.admin.dto;

import java.util.List;

public class DashboardStats {

    private Long articleCount;
    private Long userCount;
    private Long siteCount;
    private Long viewCount;
    private Long pendingReviewCount;
    private List<RecentActivity> recentActivities;
    private List<PendingArticle> pendingArticles;

    public Long getArticleCount() { return articleCount; }
    public void setArticleCount(Long articleCount) { this.articleCount = articleCount; }

    public Long getUserCount() { return userCount; }
    public void setUserCount(Long userCount) { this.userCount = userCount; }

    public Long getSiteCount() { return siteCount; }
    public void setSiteCount(Long siteCount) { this.siteCount = siteCount; }

    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }

    public Long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(Long pendingReviewCount) { this.pendingReviewCount = pendingReviewCount; }

    public List<RecentActivity> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<RecentActivity> recentActivities) { this.recentActivities = recentActivities; }

    public List<PendingArticle> getPendingArticles() { return pendingArticles; }
    public void setPendingArticles(List<PendingArticle> pendingArticles) { this.pendingArticles = pendingArticles; }

    public static class RecentActivity {
        private Long id;
        private String user;
        private String action;
        private String target;
        private String time;
        private String type;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class PendingArticle {
        private Long id;
        private String title;
        private String type;
        private String author;
        private String date;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
    }
}
