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
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long siteId;

    @Column
    private Long parentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 30)
    private String type = "channel";

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 255)
    private String fullPath;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false, length = 20)
    private String status = "enabled";

    @Column(nullable = false)
    private Boolean navVisible = true;

    @Column(nullable = false)
    private Boolean breadcrumbVisible = true;

    @Column(nullable = false)
    private Boolean publicVisible = true;

    @Column
    private Long listTemplateId;

    @Column
    private Long detailTemplateId;

    @Column(nullable = false, length = 30)
    private String aggregationMode = "manual";

    @Column(length = 1000)
    private String description;

    @Column(length = 200)
    private String seoTitle;

    @Column(length = 500)
    private String seoKeywords;

    @Column(length = 1000)
    private String seoDescription;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.type == null || this.type.isBlank()) {
            this.type = "channel";
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "enabled";
        }
        if (this.aggregationMode == null || this.aggregationMode.isBlank()) {
            this.aggregationMode = "manual";
        }
        if (this.level == null || this.level < 1) {
            this.level = 1;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
        if (this.navVisible == null) {
            this.navVisible = true;
        }
        if (this.breadcrumbVisible == null) {
            this.breadcrumbVisible = true;
        }
        if (this.publicVisible == null) {
            this.publicVisible = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getNavVisible() {
        return navVisible;
    }

    public void setNavVisible(Boolean navVisible) {
        this.navVisible = navVisible;
    }

    public Boolean getBreadcrumbVisible() {
        return breadcrumbVisible;
    }

    public void setBreadcrumbVisible(Boolean breadcrumbVisible) {
        this.breadcrumbVisible = breadcrumbVisible;
    }

    public Boolean getPublicVisible() {
        return publicVisible;
    }

    public void setPublicVisible(Boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    public Long getListTemplateId() {
        return listTemplateId;
    }

    public void setListTemplateId(Long listTemplateId) {
        this.listTemplateId = listTemplateId;
    }

    public Long getDetailTemplateId() {
        return detailTemplateId;
    }

    public void setDetailTemplateId(Long detailTemplateId) {
        this.detailTemplateId = detailTemplateId;
    }

    public String getAggregationMode() {
        return aggregationMode;
    }

    public void setAggregationMode(String aggregationMode) {
        this.aggregationMode = aggregationMode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeoTitle() {
        return seoTitle;
    }

    public void setSeoTitle(String seoTitle) {
        this.seoTitle = seoTitle;
    }

    public String getSeoKeywords() {
        return seoKeywords;
    }

    public void setSeoKeywords(String seoKeywords) {
        this.seoKeywords = seoKeywords;
    }

    public String getSeoDescription() {
        return seoDescription;
    }

    public void setSeoDescription(String seoDescription) {
        this.seoDescription = seoDescription;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
