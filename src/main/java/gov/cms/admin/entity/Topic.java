package gov.cms.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long siteId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column
    private Long templateId;

    @Column(nullable = false, length = 30)
    private String aggregationMode = "manual";

    @Column
    private Long ruleCategoryId;

    @Column(nullable = false)
    private Integer ruleLimit = 10;

    @Column(length = 200)
    private String seoTitle;

    @Column(length = 500)
    private String seoKeywords;

    @Column(length = 1000)
    private String seoDescription;

    @Column(nullable = false)
    private Boolean navVisible = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) status = "draft";
        if (aggregationMode == null || aggregationMode.isBlank()) aggregationMode = "manual";
        if (ruleLimit == null || ruleLimit < 1) ruleLimit = 10;
        if (navVisible == null) navVisible = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (ruleLimit == null || ruleLimit < 1) ruleLimit = 10;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getAggregationMode() { return aggregationMode; }
    public void setAggregationMode(String aggregationMode) { this.aggregationMode = aggregationMode; }
    public Long getRuleCategoryId() { return ruleCategoryId; }
    public void setRuleCategoryId(Long ruleCategoryId) { this.ruleCategoryId = ruleCategoryId; }
    public Integer getRuleLimit() { return ruleLimit; }
    public void setRuleLimit(Integer ruleLimit) { this.ruleLimit = ruleLimit; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
    public String getSeoKeywords() { return seoKeywords; }
    public void setSeoKeywords(String seoKeywords) { this.seoKeywords = seoKeywords; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }
    public Boolean getNavVisible() { return navVisible; }
    public void setNavVisible(Boolean navVisible) { this.navVisible = navVisible; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
