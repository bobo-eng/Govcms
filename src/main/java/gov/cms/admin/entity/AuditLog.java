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
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String actionType;

    @Column(nullable = false, length = 60)
    private String objectType;

    @Column
    private Long objectId;

    @Column
    private Long siteId;

    @Column(length = 100)
    private String operatorName;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(length = 500)
    private String summary;

    @Column(length = 1000)
    private String failureReason;

    @Column
    private Long relatedJobId;

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
        if (result == null || result.isBlank()) {
            result = "success";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long objectId) { this.objectId = objectId; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Long getRelatedJobId() { return relatedJobId; }
    public void setRelatedJobId(Long relatedJobId) { this.relatedJobId = relatedJobId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
