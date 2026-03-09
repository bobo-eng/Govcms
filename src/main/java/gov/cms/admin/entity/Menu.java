package gov.cms.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "menus")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String path;

    @Column(length = 50)
    private String icon;

    @Column(length = 50)
    private Long parentId;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(length = 50)
    private String permissionId;

    @Column(nullable = false)
    private Boolean visible = true;

    @Column(nullable = false, length = 20)
    private String status = "enabled";

    @Column(length = 20)
    private String menuGroup = "";

    @Column(length = 50)
    private String groupTitle = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getPermissionId() { return permissionId; }
    public void setPermissionId(String permissionId) { this.permissionId = permissionId; }

    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMenuGroup() { return menuGroup; }
    public void setMenuGroup(String menuGroup) { this.menuGroup = menuGroup; }

    public String getGroupTitle() { return groupTitle; }
    public void setGroupTitle(String groupTitle) { this.groupTitle = groupTitle; }
}
