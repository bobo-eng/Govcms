package gov.cms.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_versions")
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private Integer versionNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String layoutSchema;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String blockSchema;

    @Column(columnDefinition = "TEXT")
    private String seoSchema;

    @Column(columnDefinition = "TEXT")
    private String styleSchema;

    @Column(length = 1000)
    private String changeLog;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "system";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getLayoutSchema() {
        return layoutSchema;
    }

    public void setLayoutSchema(String layoutSchema) {
        this.layoutSchema = layoutSchema;
    }

    public String getBlockSchema() {
        return blockSchema;
    }

    public void setBlockSchema(String blockSchema) {
        this.blockSchema = blockSchema;
    }

    public String getSeoSchema() {
        return seoSchema;
    }

    public void setSeoSchema(String seoSchema) {
        this.seoSchema = seoSchema;
    }

    public String getStyleSchema() {
        return styleSchema;
    }

    public void setStyleSchema(String styleSchema) {
        this.styleSchema = styleSchema;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
