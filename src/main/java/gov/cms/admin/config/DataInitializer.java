package gov.cms.admin.config;

import gov.cms.admin.entity.Menu;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.entity.Role;
import gov.cms.admin.repository.MenuRepository;
import gov.cms.admin.repository.PermissionRepository;
import gov.cms.admin.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    @Transactional
    public CommandLineRunner initData(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            MenuRepository menuRepository) {
        
        return args -> {
            // Initialize permissions
            if (permissionRepository.count() > 0) {
                return;
            }

            // Create permissions
            List<Permission> permissions = Arrays.asList(
                // System Management
                createPermission("sys", "系统管理", "sys", "menu", null, "/system", "SettingOutlined", 1),
                createPermission("sys:user", "用户管理", "sys:user", "menu", "sys", "/system/users", "UserOutlined", 1),
                createPermission("sys:user:view", "查看用户", "sys:user:view", "button", "sys:user", null, null, 1),
                createPermission("sys:user:create", "新增用户", "sys:user:create", "button", "sys:user", null, null, 2),
                createPermission("sys:user:update", "编辑用户", "sys:user:update", "button", "sys:user", null, null, 3),
                createPermission("sys:user:delete", "删除用户", "sys:user:delete", "button", "sys:user", null, null, 4),
                createPermission("sys:role", "角色管理", "sys:role", "menu", "sys", "/system/roles", "TeamOutlined", 2),
                createPermission("sys:role:view", "查看角色", "sys:role:view", "button", "sys:role", null, null, 1),
                createPermission("sys:role:create", "新增角色", "sys:role:create", "button", "sys:role", null, null, 2),
                createPermission("sys:role:update", "编辑角色", "sys:role:update", "button", "sys:role", null, null, 3),
                createPermission("sys:role:delete", "删除角色", "sys:role:delete", "button", "sys:role", null, null, 4),
                
                // Content Management
                createPermission("content", "内容管理", "content", "menu", null, "/content", "FileTextOutlined", 2),
                createPermission("content:article", "文章管理", "content:article", "menu", "content", "/content/articles", "FileTextOutlined", 1),
                createPermission("content:article:view", "查看文章", "content:article:view", "button", "content:article", null, null, 1),
                createPermission("content:article:create", "新增文章", "content:article:create", "button", "content:article", null, null, 2),
                createPermission("content:article:update", "编辑文章", "content:article:update", "button", "content:article", null, null, 3),
                createPermission("content:article:delete", "删除文章", "content:article:delete", "button", "content:article", null, null, 4),
                createPermission("content:article:publish", "发布文章", "content:article:publish", "button", "content:article", null, null, 5),
                createPermission("content:category", "栏目管理", "content:category", "menu", "content", "/content/categories", "FolderOutlined", 2),
                createPermission("content:category:view", "查看栏目", "content:category:view", "button", "content:category", null, null, 1),
                createPermission("content:category:create", "新增栏目", "content:category:create", "button", "content:category", null, null, 2),
                createPermission("content:category:update", "编辑栏目", "content:category:update", "button", "content:category", null, null, 3),
                createPermission("content:category:delete", "删除栏目", "content:category:delete", "button", "content:category", null, null, 4),
                
                // Site Management
                createPermission("site", "站点管理", "site", "menu", null, "/site", "GlobalOutlined", 3),
                createPermission("site:manage", "站点管理", "site:manage", "menu", "site", "/site/list", "GlobalOutlined", 1),
                createPermission("site:manage:view", "查看站点", "site:manage:view", "button", "site:manage", null, null, 1),
                createPermission("site:manage:create", "创建站点", "site:manage:create", "button", "site:manage", null, null, 2),
                createPermission("site:manage:update", "编辑站点", "site:manage:update", "button", "site:manage", null, null, 3),
                createPermission("site:manage:delete", "删除站点", "site:manage:delete", "button", "site:manage", null, null, 4),
                
                // Media Management
                createPermission("media", "媒体管理", "media", "menu", null, "/media", "CloudOutlined", 4),
                createPermission("media:manage", "媒体库", "media:manage", "menu", "media", "/media/library", "CloudOutlined", 1),
                createPermission("media:manage:view", "查看媒体", "media:manage:view", "button", "media:manage", null, null, 1),
                createPermission("media:manage:upload", "上传媒体", "media:manage:upload", "button", "media:manage", null, null, 2),
                createPermission("media:manage:delete", "删除媒体", "media:manage:delete", "button", "media:manage", null, null, 3)
            );
            
            permissionRepository.saveAll(permissions);
            
            // Create roles
            Role adminRole = new Role();
            adminRole.setName("系统管理员");
            adminRole.setCode("admin");
            adminRole.setDescription("拥有系统所有权限");
            adminRole.setStatus("enabled");
            adminRole.setSort(1);
            adminRole.setPermissions(new HashSet<>(permissions));
            
            Role editorRole = new Role();
            editorRole.setName("内容编辑");
            editorRole.setCode("editor");
            editorRole.setDescription("负责内容创建和编辑");
            editorRole.setStatus("enabled");
            editorRole.setSort(2);
            Set<Permission> editorPerms = new HashSet<>();
            editorPerms.addAll(permissions.stream()
                .filter(p -> p.getCode().startsWith("content:"))
                .toList());
            editorRole.setPermissions(editorPerms);
            
            Role viewerRole = new Role();
            viewerRole.setName("普通用户");
            viewerRole.setCode("viewer");
            viewerRole.setDescription("仅查看内容");
            viewerRole.setStatus("enabled");
            viewerRole.setSort(3);
            Set<Permission> viewerPerms = new HashSet<>();
            viewerPerms.add(permissions.stream().filter(p -> p.getCode().equals("content:article:view")).findFirst().orElse(null));
            viewerPerms.add(permissions.stream().filter(p -> p.getCode().equals("content:category:view")).findFirst().orElse(null));
            viewerPerms.remove(null);
            viewerRole.setPermissions(viewerPerms);
            
            roleRepository.saveAll(Arrays.asList(adminRole, editorRole, viewerRole));
            
            // Create menus
            List<Menu> menus = Arrays.asList(
                createMenu("仪表盘", "/dashboard", "DashboardOutlined", null, 1, "content:article:view"),
                createMenu("用户管理", "/users", "UserOutlined", null, 2, "sys:user"),
                createMenu("角色管理", "/roles", "TeamOutlined", null, 3, "sys:role"),
                createMenu("内容管理", "/content", "FileTextOutlined", null, 4, "content"),
                createMenu("站点管理", "/sites", "GlobalOutlined", null, 5, "site:manage"),
                createMenu("媒体管理", "/media", "CloudOutlined", null, 6, "media:manage")
            );
            
            menuRepository.saveAll(menus);
            
            System.out.println("=== Permissions, Roles and Menus initialized ===");
        };
    }
    
    private Permission createPermission(String id, String name, String code, String type, 
            String parentId, String path, String icon, int sort) {
        Permission perm = new Permission();
        perm.setId(id);
        perm.setName(name);
        perm.setCode(code);
        perm.setType(type);
        perm.setParentId(parentId);
        perm.setPath(path);
        perm.setIcon(icon);
        perm.setSort(sort);
        return perm;
    }
    
    private Menu createMenu(String name, String path, String icon, Long parentId, int sort, String permissionId) {
        Menu menu = new Menu();
        menu.setName(name);
        menu.setPath(path);
        menu.setIcon(icon);
        menu.setParentId(parentId);
        menu.setSort(sort);
        menu.setPermissionId(permissionId);
        menu.setVisible(true);
        menu.setStatus("enabled");
        return menu;
    }
}
