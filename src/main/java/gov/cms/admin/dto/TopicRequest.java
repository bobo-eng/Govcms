package gov.cms.admin.dto;

public class TopicRequest {
    private Long siteId;
    private String name;
    private String code;
    private String slug;
    private String summary;
    private String status;
    private Long templateId;
    private String aggregationMode;
    private Long ruleCategoryId;
    private Integer ruleLimit;
    private String seoTitle;
    private String seoKeywords;
    private String seoDescription;
    private Boolean navVisible;

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
}
