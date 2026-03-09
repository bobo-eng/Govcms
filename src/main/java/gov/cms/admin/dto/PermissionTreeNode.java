package gov.cms.admin.dto;

import gov.cms.admin.entity.Permission;

import java.util.ArrayList;
import java.util.List;

public class PermissionTreeNode {

    private String id;
    private String name;
    private String code;
    private String type;
    private String parentId;
    private String path;
    private String icon;
    private Integer sort;
    private List<PermissionTreeNode> children = new ArrayList<>();

    public static PermissionTreeNode from(Permission permission) {
        PermissionTreeNode node = new PermissionTreeNode();
        node.setId(permission.getId());
        node.setName(permission.getName());
        node.setCode(permission.getCode());
        node.setType(permission.getType());
        node.setParentId(permission.getParentId());
        node.setPath(permission.getPath());
        node.setIcon(permission.getIcon());
        node.setSort(permission.getSort());
        return node;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
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

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public List<PermissionTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<PermissionTreeNode> children) {
        this.children = children;
    }
}
