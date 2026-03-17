package gov.cms.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "article_lifecycle_histories")
public class ArticleLifecycleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ArticleLifecycleAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ArticleStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ArticleStatus toStatus;

    @Column(nullable = false, length = 100)
    private String operatorName;

    @Column(length = 1000)
    private String reason;

    @Column
    private Long publishJobId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (operatorName == null || operatorName.isBlank()) {
            operatorName = "system";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public ArticleLifecycleAction getAction() { return action; }
    public void setAction(ArticleLifecycleAction action) { this.action = action; }
    public ArticleStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(ArticleStatus fromStatus) { this.fromStatus = fromStatus; }
    public ArticleStatus getToStatus() { return toStatus; }
    public void setToStatus(ArticleStatus toStatus) { this.toStatus = toStatus; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getPublishJobId() { return publishJobId; }
    public void setPublishJobId(Long publishJobId) { this.publishJobId = publishJobId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}