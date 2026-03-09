package gov.cms.admin.dto;

import gov.cms.admin.entity.Menu;

import java.util.ArrayList;
import java.util.List;

public class MenuTreeNode {

    private Long id;
    private String name;
    private String path;
    private String icon;
    private Long parentId;
    private Integer sort;
    private String permissionId;
    private Boolean visible;
    private String status;
    private String menuGroup;
    private String groupTitle;
    private List<MenuTreeNode> children = new ArrayList<>();

    public static MenuTreeNode from(Menu menu) {
        MenuTreeNode node = new MenuTreeNode();
        node.setId(menu.getId());
        node.setName(menu.getName());
        node.setPath(menu.getPath());
        node.setIcon(menu.getIcon());
        node.setParentId(menu.getParentId());
        node.setSort(menu.getSort());
        node.setPermissionId(menu.getPermissionId());
        node.setVisible(menu.getVisible());
        node.setStatus(menu.getStatus());
        node.setMenuGroup(menu.getMenuGroup());
        node.setGroupTitle(menu.getGroupTitle());
        return node;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMenuGroup() {
        return menuGroup;
    }

    public void setMenuGroup(String menuGroup) {
        this.menuGroup = menuGroup;
    }

    public String getGroupTitle() {
        return groupTitle;
    }

    public void setGroupTitle(String groupTitle) {
        this.groupTitle = groupTitle;
    }

    public List<MenuTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<MenuTreeNode> children) {
        this.children = children;
    }
}
