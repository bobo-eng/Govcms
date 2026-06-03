package gov.cms.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_index_entries", indexes = {
        @Index(name = "idx_search_site_type_object", columnList = "siteId, objectType, objectId", unique = true),
        @Index(name = "idx_search_site_status", columnList = "siteId, status")
})
@Indexed
public class SearchIndexEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @GenericField
    private Long siteId;

    @Column(nullable = false, length = 40)
    @KeywordField
    private String objectType;

    @Column(nullable = false)
    private Long objectId;

    @Column(nullable = false, length = 500)
    @FullTextField(analyzer = "standard")
    @KeywordField(name = "title_sort", sortable = Sortable.YES)
    private String title;

    @Column(length = 2000)
    @FullTextField(analyzer = "standard")
    private String summary;

    @Column(length = 1000)
    @FullTextField(analyzer = "standard")
    private String keywords;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false, length = 20)
    @KeywordField(sortable = Sortable.YES)
    private String status;

    @Column
    @GenericField(sortable = Sortable.YES)
    private LocalDateTime publishedAt;

    @Column
    @GenericField(sortable = Sortable.YES)
    private Long categoryId;

    @Column(length = 200)
    @FullTextField(analyzer = "standard")
    private String categoryName;

    @Column(length = 200)
    @FullTextField(analyzer = "standard")
    private String topicName;

    @Column(columnDefinition = "TEXT")
    @FullTextField(analyzer = "standard")
    private String searchText;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long objectId) { this.objectId = objectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
