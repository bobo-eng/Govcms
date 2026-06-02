package gov.cms.admin.dto;

public class NavigationItemRequest {
    private Long siteId;
    private Long parentId;
    private String name;
    private String code;
    private String targetType;
    private Long targetId;
    private String targetValue;
    private Integer sortOrder;
    private String status;
    private Boolean primaryNav;
    private Boolean breadcrumbEnabled;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getPrimaryNav() { return primaryNav; }
    public void setPrimaryNav(Boolean primaryNav) { this.primaryNav = primaryNav; }
    public Boolean getBreadcrumbEnabled() { return breadcrumbEnabled; }
    public void setBreadcrumbEnabled(Boolean breadcrumbEnabled) { this.breadcrumbEnabled = breadcrumbEnabled; }
}
