package gov.cms.admin.dto;

public class CategoryRequest {

    private Long siteId;
    private Long parentId;
    private String name;
    private String code;
    private String type;
    private String slug;
    private Integer sortOrder;
    private String status;
    private Boolean navVisible;
    private Boolean breadcrumbVisible;
    private Boolean publicVisible;
    private Long listTemplateId;
    private Long detailTemplateId;
    private String aggregationMode;
    private String description;
    private String seoTitle;
    private String seoKeywords;
    private String seoDescription;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getNavVisible() { return navVisible; }
    public void setNavVisible(Boolean navVisible) { this.navVisible = navVisible; }
    public Boolean getBreadcrumbVisible() { return breadcrumbVisible; }
    public void setBreadcrumbVisible(Boolean breadcrumbVisible) { this.breadcrumbVisible = breadcrumbVisible; }
    public Boolean getPublicVisible() { return publicVisible; }
    public void setPublicVisible(Boolean publicVisible) { this.publicVisible = publicVisible; }
    public Long getListTemplateId() { return listTemplateId; }
    public void setListTemplateId(Long listTemplateId) { this.listTemplateId = listTemplateId; }
    public Long getDetailTemplateId() { return detailTemplateId; }
    public void setDetailTemplateId(Long detailTemplateId) { this.detailTemplateId = detailTemplateId; }
    public String getAggregationMode() { return aggregationMode; }
    public void setAggregationMode(String aggregationMode) { this.aggregationMode = aggregationMode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
    public String getSeoKeywords() { return seoKeywords; }
    public void setSeoKeywords(String seoKeywords) { this.seoKeywords = seoKeywords; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }
}
