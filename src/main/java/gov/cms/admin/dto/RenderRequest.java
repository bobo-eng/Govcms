package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class RenderRequest {

    private Long siteId;
    private Long templateId;
    private Long templateVersionId;
    private String pageType;
    private String sourceType;
    private Long sourceId;
    private String mode;
    private String operation;
    private List<Long> includeArticleIds = new ArrayList<>();
    private List<Long> excludeArticleIds = new ArrayList<>();

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public List<Long> getIncludeArticleIds() {
        return includeArticleIds;
    }

    public void setIncludeArticleIds(List<Long> includeArticleIds) {
        this.includeArticleIds = includeArticleIds == null ? new ArrayList<>() : new ArrayList<>(includeArticleIds);
    }

    public List<Long> getExcludeArticleIds() {
        return excludeArticleIds;
    }

    public void setExcludeArticleIds(List<Long> excludeArticleIds) {
        this.excludeArticleIds = excludeArticleIds == null ? new ArrayList<>() : new ArrayList<>(excludeArticleIds);
    }
}