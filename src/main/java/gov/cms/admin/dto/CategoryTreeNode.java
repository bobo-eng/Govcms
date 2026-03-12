package gov.cms.admin.dto;

import gov.cms.admin.entity.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryTreeNode {

    private Long id;
    private Long siteId;
    private Long parentId;
    private String name;
    private String code;
    private String type;
    private String slug;
    private String fullPath;
    private Integer level;
    private Integer sortOrder;
    private String status;
    private Boolean navVisible;
    private Boolean breadcrumbVisible;
    private Boolean publicVisible;
    private Long listTemplateId;
    private Long detailTemplateId;
    private String aggregationMode;
    private String description;
    private List<CategoryTreeNode> children = new ArrayList<>();

    public static CategoryTreeNode from(Category category) {
        CategoryTreeNode node = new CategoryTreeNode();
        node.setId(category.getId());
        node.setSiteId(category.getSiteId());
        node.setParentId(category.getParentId());
        node.setName(category.getName());
        node.setCode(category.getCode());
        node.setType(category.getType());
        node.setSlug(category.getSlug());
        node.setFullPath(category.getFullPath());
        node.setLevel(category.getLevel());
        node.setSortOrder(category.getSortOrder());
        node.setStatus(category.getStatus());
        node.setNavVisible(category.getNavVisible());
        node.setBreadcrumbVisible(category.getBreadcrumbVisible());
        node.setPublicVisible(category.getPublicVisible());
        node.setListTemplateId(category.getListTemplateId());
        node.setDetailTemplateId(category.getDetailTemplateId());
        node.setAggregationMode(category.getAggregationMode());
        node.setDescription(category.getDescription());
        return node;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
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
    public List<CategoryTreeNode> getChildren() { return children; }
    public void setChildren(List<CategoryTreeNode> children) { this.children = children; }
}
