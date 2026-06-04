package gov.cms.admin.config;

import gov.cms.admin.entity.Menu;
import gov.cms.admin.entity.Notification;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.MenuRepository;
import gov.cms.admin.repository.NotificationRepository;
import gov.cms.admin.repository.PermissionRepository;
import gov.cms.admin.repository.RoleRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class DataInitializer {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@govcms.local";
    private static final String DEFAULT_ADMIN_NAME = "超级管理员";

    @Bean
    public CommandLineRunner initData(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            MenuRepository menuRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NotificationRepository notificationRepository,
            DataSource dataSource
    ) {
        return args -> {
            seedPermissions(permissionRepository);
            seedRoles(permissionRepository, roleRepository);
            seedMenus(menuRepository);
            seedDefaultAdmin(userRepository, roleRepository, passwordEncoder);
            ensureNotificationTable(dataSource);
            seedNotifications(notificationRepository, userRepository);
        };
    }

    private void seedPermissions(PermissionRepository permissionRepository) {
        for (Permission desiredPermission : buildPermissions()) {
            Permission permission = permissionRepository.findById(desiredPermission.getId()).orElseGet(Permission::new);
            permission.setId(desiredPermission.getId());
            permission.setName(desiredPermission.getName());
            permission.setCode(desiredPermission.getCode());
            permission.setType(desiredPermission.getType());
            permission.setParentId(desiredPermission.getParentId());
            permission.setPath(desiredPermission.getPath());
            permission.setIcon(desiredPermission.getIcon());
            permission.setSort(desiredPermission.getSort());
            permissionRepository.save(permission);
        }
    }

    private List<Permission> buildPermissions() {
        return Arrays.asList(
                createPermission("sys", "系统管理", "sys", "menu", null, "/system", "SettingOutlined", 1),
                createPermission("sys:dashboard", "仪表盘", "sys:dashboard:view", "menu", "sys", "/dashboard", "DashboardOutlined", 0),
                createPermission("sys:audit", "审计日志", "sys:audit:view", "menu", "sys", "/system/audit-logs", "AuditOutlined", 5),
                createPermission("sys:user", "用户管理", "sys:user", "menu", "sys", "/users", "UserOutlined", 1),
                createPermission("sys:user:view", "查看用户", "sys:user:view", "button", "sys:user", null, null, 1),
                createPermission("sys:user:create", "创建用户", "sys:user:create", "button", "sys:user", null, null, 2),
                createPermission("sys:user:update", "更新用户", "sys:user:update", "button", "sys:user", null, null, 3),
                createPermission("sys:user:delete", "删除用户", "sys:user:delete", "button", "sys:user", null, null, 4),
                createPermission("sys:user:reset-password", "重置密码", "sys:user:reset-password", "button", "sys:user", null, null, 5),

                createPermission("sys:role", "角色管理", "sys:role", "menu", "sys", "/roles", "TeamOutlined", 2),
                createPermission("sys:role:view", "查看角色", "sys:role:view", "button", "sys:role", null, null, 1),
                createPermission("sys:role:create", "创建角色", "sys:role:create", "button", "sys:role", null, null, 2),
                createPermission("sys:role:update", "更新角色", "sys:role:update", "button", "sys:role", null, null, 3),
                createPermission("sys:role:delete", "删除角色", "sys:role:delete", "button", "sys:role", null, null, 4),

                createPermission("sys:permission", "权限管理", "sys:permission", "menu", "sys", "/permissions", "LockOutlined", 3),
                createPermission("sys:permission:view", "查看权限", "sys:permission:view", "button", "sys:permission", null, null, 1),
                createPermission("sys:permission:create", "创建权限", "sys:permission:create", "button", "sys:permission", null, null, 2),
                createPermission("sys:permission:update", "更新权限", "sys:permission:update", "button", "sys:permission", null, null, 3),
                createPermission("sys:permission:delete", "删除权限", "sys:permission:delete", "button", "sys:permission", null, null, 4),

                createPermission("sys:menu", "菜单管理", "sys:menu", "menu", "sys", "/menus", "MenuOutlined", 4),
                createPermission("sys:menu:view", "查看菜单", "sys:menu:view", "button", "sys:menu", null, null, 1),
                createPermission("sys:menu:create", "创建菜单", "sys:menu:create", "button", "sys:menu", null, null, 2),
                createPermission("sys:menu:update", "更新菜单", "sys:menu:update", "button", "sys:menu", null, null, 3),
                createPermission("sys:menu:delete", "删除菜单", "sys:menu:delete", "button", "sys:menu", null, null, 4),

                createPermission("content", "内容管理", "content", "menu", null, "/content", "FileTextOutlined", 2),
                createPermission("content:article", "内容管理", "content:article", "menu", "content", "/content", "FileTextOutlined", 1),
                createPermission("content:article:view", "查看内容", "content:article:view", "button", "content:article", null, null, 1),
                createPermission("content:article:create", "创建内容", "content:article:create", "button", "content:article", null, null, 2),
                createPermission("content:article:update", "更新内容", "content:article:update", "button", "content:article", null, null, 3),
                createPermission("content:article:delete", "删除内容", "content:article:delete", "button", "content:article", null, null, 4),
                createPermission("content:article:submit-review", "提交审核", "content:article:submit-review", "button", "content:article", null, null, 5),
                createPermission("content:article:review", "审核通过", "content:article:review", "button", "content:article", null, null, 6),
                createPermission("content:article:reject", "审核驳回", "content:article:reject", "button", "content:article", null, null, 7),
                createPermission("content:article:offline", "内容下线", "content:article:offline", "button", "content:article", null, null, 8),
                createPermission("content:article:history:view", "查看历史", "content:article:history:view", "button", "content:article", null, null, 9),

                createPermission("content:category", "栏目管理", "content:category", "menu", "content", "/content/categories", "FolderOutlined", 2),
                createPermission("content:category:view", "查看栏目", "content:category:view", "button", "content:category", null, null, 1),
                createPermission("content:category:create", "创建栏目", "content:category:create", "button", "content:category", null, null, 2),
                createPermission("content:category:update", "更新栏目", "content:category:update", "button", "content:category", null, null, 3),
                createPermission("content:category:delete", "删除栏目", "content:category:delete", "button", "content:category", null, null, 4),

                createPermission("template:manage", "模板管理", "template:manage", "menu", "content", "/content/templates", "LayoutOutlined", 3),
                createPermission("template:manage:view", "查看模板", "template:manage:view", "button", "template:manage", null, null, 1),
                createPermission("template:manage:create", "创建模板", "template:manage:create", "button", "template:manage", null, null, 2),
                createPermission("template:manage:update", "更新模板", "template:manage:update", "button", "template:manage", null, null, 3),
                createPermission("template:manage:bind", "绑定模板", "template:manage:bind", "button", "template:manage", null, null, 4),
                createPermission("template:manage:preview", "预览模板", "template:manage:preview", "button", "template:manage", null, null, 5),
                createPermission("template:manage:delete", "删除模板", "template:manage:delete", "button", "template:manage", null, null, 6),

                createPermission("navigation:manage", "导航管理", "navigation:manage", "menu", "content", "/navigation", "MenuOutlined", 4),
                createPermission("navigation:manage:view", "查看导航", "navigation:manage:view", "button", "navigation:manage", null, null, 1),
                createPermission("navigation:manage:create", "创建导航", "navigation:manage:create", "button", "navigation:manage", null, null, 2),
                createPermission("navigation:manage:update", "更新导航", "navigation:manage:update", "button", "navigation:manage", null, null, 3),
                createPermission("navigation:manage:delete", "删除导航", "navigation:manage:delete", "button", "navigation:manage", null, null, 4),

                createPermission("topic:manage", "专题管理", "topic:manage", "menu", "content", "/topics", "FileTextOutlined", 5),
                createPermission("topic:manage:view", "查看专题", "topic:manage:view", "button", "topic:manage", null, null, 1),
                createPermission("topic:manage:create", "创建专题", "topic:manage:create", "button", "topic:manage", null, null, 2),
                createPermission("topic:manage:update", "更新专题", "topic:manage:update", "button", "topic:manage", null, null, 3),
                createPermission("topic:manage:delete", "删除专题", "topic:manage:delete", "button", "topic:manage", null, null, 4),

                createPermission("publish:center", "发布中心", "publish:center", "menu", "content", "/content/publish", "SendOutlined", 6),
                createPermission("publish:center:view", "查看发布中心", "publish:center:view", "button", "publish:center", null, null, 1),
                createPermission("publish:center:execute", "执行发布", "publish:center:execute", "button", "publish:center", null, null, 2),
                createPermission("publish:center:rollback", "执行回滚", "publish:center:rollback", "button", "publish:center", null, null, 3),
                createPermission("publish:center:artifact:view", "查看产物", "publish:center:artifact:view", "button", "publish:center", null, null, 4),
                createPermission("publish:center:log:view", "查看日志", "publish:center:log:view", "button", "publish:center", null, null, 5),

                createPermission("site", "站点", "site", "menu", null, "/sites", "GlobalOutlined", 3),
                createPermission("site:manage", "站点管理", "site:manage", "menu", "site", "/sites", "GlobalOutlined", 1),
                createPermission("site:manage:view", "查看站点", "site:manage:view", "button", "site:manage", null, null, 1),
                createPermission("site:manage:self", "管理本站点", "site:manage:self", "button", "site:manage", null, null, 2),
                createPermission("site:manage:create", "创建站点", "site:manage:create", "button", "site:manage", null, null, 3),
                createPermission("site:manage:update", "更新站点", "site:manage:update", "button", "site:manage", null, null, 4),
                createPermission("site:manage:delete", "删除站点", "site:manage:delete", "button", "site:manage", null, null, 5),

                createPermission("media", "媒体", "media", "menu", null, "/media", "CloudOutlined", 4),
                createPermission("media:manage", "媒体管理", "media:manage", "menu", "media", "/media", "CloudOutlined", 1),
                createPermission("media:manage:view", "查看媒体", "media:manage:view", "button", "media:manage", null, null, 1),
                createPermission("media:manage:upload", "上传媒体", "media:manage:upload", "button", "media:manage", null, null, 2),
                createPermission("media:manage:delete", "删除媒体", "media:manage:delete", "button", "media:manage", null, null, 3),
                createPermission("search:ops", "搜索运营", "search:ops", "menu", null, "/search-ops", "SearchOutlined", 9),
                createPermission("search:ops:view", "查看搜索运营", "search:ops:view", "button", "search:ops", null, null, 1),
                createPermission("search:ops:rebuild", "重建搜索索引", "search:ops:rebuild", "button", "search:ops", null, null, 2)
        );
    }

    private void seedRoles(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        List<Permission> permissions = permissionRepository.findAllByOrderBySortAscIdAsc();
        if (permissions.isEmpty()) {
            return;
        }

        upsertRole(roleRepository.findByCode("admin").orElseGet(Role::new), roleRepository,
                "超级管理员", "admin", "平台全局治理角色", 1, new LinkedHashSet<>(permissions));

        Set<Permission> siteAdminPermissions = permissions.stream()
                .filter(permission -> Set.of(
                        "sys:dashboard:view", "sys:audit:view",
                        "content", "content:article:view",
                        "content:category", "content:category:view", "content:category:create", "content:category:update", "content:category:delete",
                        "template:manage", "template:manage:view", "template:manage:create", "template:manage:update", "template:manage:bind", "template:manage:preview", "template:manage:delete",
                        "navigation:manage", "navigation:manage:view", "navigation:manage:create", "navigation:manage:update", "navigation:manage:delete",
                        "topic:manage", "topic:manage:view", "topic:manage:create", "topic:manage:update", "topic:manage:delete",
                        "publish:center", "publish:center:view", "publish:center:execute", "publish:center:rollback", "publish:center:artifact:view", "publish:center:log:view",
                        "site", "site:manage", "site:manage:self", "site:manage:update",
                        "media", "media:manage", "media:manage:view", "media:manage:upload", "media:manage:delete",
                        "search:ops", "search:ops:view", "search:ops:rebuild"
                ).contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("site_admin").orElseGet(Role::new), roleRepository,
                "站点管理员", "site_admin", "本站点治理角色", 2, siteAdminPermissions);

        Set<Permission> editorPermissions = permissions.stream()
                .filter(permission -> Set.of(
                        "sys:dashboard:view",
                        "content", "content:article", "content:article:view", "content:article:create", "content:article:update",
                        "content:article:delete", "content:article:submit-review", "content:article:history:view",
                        "content:category", "content:category:view", "template:manage", "template:manage:view", "template:manage:preview"
                ).contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("editor").orElseGet(Role::new), roleRepository,
                "编辑", "editor", "内容生产角色", 3, editorPermissions);

        Set<Permission> reviewerPermissions = permissions.stream()
                .filter(permission -> Set.of(
                        "sys:dashboard:view",
                        "content", "content:article:view", "content:article:review", "content:article:reject", "content:article:history:view",
                        "template:manage:view", "template:manage:preview"
                ).contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("reviewer").orElseGet(Role::new), roleRepository,
                "审核员", "reviewer", "内容审核角色", 4, reviewerPermissions);

        Set<Permission> publisherPermissions = permissions.stream()
                .filter(permission -> permission.getCode().startsWith("publish:center")
                        || Set.of("sys:dashboard:view", "content:article:view", "content:article:offline", "content:article:history:view", "site:manage:view", "template:manage:view", "content:category:view", "navigation:manage:view", "topic:manage:view").contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("publisher").orElseGet(Role::new), roleRepository,
                "发布员", "publisher", "发布执行角色", 5, publisherPermissions);

        Set<Permission> viewerPermissions = permissions.stream()
                .filter(permission -> Set.of("sys:dashboard:view", "content:article:view", "content:category:view", "template:manage:view", "site:manage:view", "media:manage:view").contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("viewer").orElseGet(Role::new), roleRepository,
                "只读", "viewer", "只读访问角色", 6, viewerPermissions);
    }

    private void seedMenus(MenuRepository menuRepository) {
        List<String> legacyPaths = List.of(
            "/users", "/roles", "/permissions", "/menus",
            "/navigation", "/topics", "/sites", "/media", "/search-ops"
        );
        for (String path : legacyPaths) {
            menuRepository.findByPath(path).ifPresent(menuRepository::delete);
        }

        upsertMenu(menuRepository, createMenu("仪表盘", "/dashboard", "DashboardOutlined", null, 1, "sys:dashboard:view"), null);

        Menu systemMenu = upsertMenu(menuRepository, createMenu("系统管理", "/system", "SettingOutlined", null, 2, "sys"), null);
        upsertMenu(menuRepository, createMenu("用户管理", "/system/users", "UserOutlined", systemMenu.getId(), 1, "sys:user"), systemMenu.getId());
        upsertMenu(menuRepository, createMenu("角色管理", "/system/roles", "TeamOutlined", systemMenu.getId(), 2, "sys:role"), systemMenu.getId());
        upsertMenu(menuRepository, createMenu("权限管理", "/system/permissions", "LockOutlined", systemMenu.getId(), 3, "sys:permission"), systemMenu.getId());
        upsertMenu(menuRepository, createMenu("菜单管理", "/system/menus", "MenuOutlined", systemMenu.getId(), 4, "sys:menu"), systemMenu.getId());
        upsertMenu(menuRepository, createMenu("审计日志", "/system/audit-logs", "AuditOutlined", systemMenu.getId(), 5, "sys:audit:view"), systemMenu.getId());

        Menu contentMenu = upsertMenu(menuRepository, createMenu("内容管理", "/content", "FileTextOutlined", null, 3, "content"), null);
        upsertMenu(menuRepository, createMenu("审核工作区", "/content/review", "AuditOutlined", contentMenu.getId(), 1, "content:article:review"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("内容管理", "/content/articles", "FileTextOutlined", contentMenu.getId(), 2, "content:article"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("栏目管理", "/content/categories", "FolderOutlined", contentMenu.getId(), 3, "content:category"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("模板管理", "/content/templates", "LayoutOutlined", contentMenu.getId(), 4, "template:manage"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("导航管理", "/content/navigation", "MenuOutlined", contentMenu.getId(), 5, "navigation:manage"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("专题管理", "/content/topics", "FileTextOutlined", contentMenu.getId(), 6, "topic:manage"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("发布中心", "/content/publish", "SendOutlined", contentMenu.getId(), 7, "publish:center"), contentMenu.getId());

        Menu siteOpsMenu = upsertMenu(menuRepository, createMenu("站点运营", "/site-ops", "GlobalOutlined", null, 4, "site:manage"), null);
        upsertMenu(menuRepository, createMenu("站点管理", "/site-ops/sites", "GlobalOutlined", siteOpsMenu.getId(), 1, "site:manage"), siteOpsMenu.getId());
        upsertMenu(menuRepository, createMenu("媒体管理", "/site-ops/media", "CloudOutlined", siteOpsMenu.getId(), 2, "media:manage"), siteOpsMenu.getId());
        upsertMenu(menuRepository, createMenu("搜索运营", "/site-ops/search-ops", "SearchOutlined", siteOpsMenu.getId(), 3, "search:ops"), siteOpsMenu.getId());
    }

    private Menu upsertMenu(MenuRepository menuRepository, Menu desiredMenu, Long parentId) {
        Menu menu = menuRepository.findByPermissionId(desiredMenu.getPermissionId())
                .or(() -> menuRepository.findByPath(desiredMenu.getPath()))
                .orElseGet(Menu::new);
        menu.setName(desiredMenu.getName());
        menu.setPath(desiredMenu.getPath());
        menu.setIcon(desiredMenu.getIcon());
        menu.setParentId(parentId);
        menu.setSort(desiredMenu.getSort());
        menu.setPermissionId(desiredMenu.getPermissionId());
        menu.setVisible(desiredMenu.getVisible());
        menu.setStatus(desiredMenu.getStatus());
        menu.setMenuGroup(desiredMenu.getMenuGroup());
        menu.setGroupTitle(desiredMenu.getGroupTitle());
        return menuRepository.save(menu);
    }

    private void seedDefaultAdmin(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        Role adminRole = roleRepository.findByCode("admin").orElse(null);
        if (adminRole == null) {
            return;
        }

        User adminUser = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).orElse(null);
        if (adminUser == null) {
            User user = new User();
            user.setUsername(DEFAULT_ADMIN_USERNAME);
            user.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            user.setEmail(DEFAULT_ADMIN_EMAIL);
            user.setFullName(DEFAULT_ADMIN_NAME);
            user.setEnabled(true);
            user.setRoles(new LinkedHashSet<>(Set.of(adminRole)));
            userRepository.save(user);
            return;
        }

        if (adminUser.getRoles() == null) {
            adminUser.setRoles(new LinkedHashSet<>());
        }
        if (adminUser.getRoles().stream().noneMatch(role -> "admin".equals(role.getCode()))) {
            adminUser.getRoles().add(adminRole);
        }
        if (adminUser.getFullName() == null || adminUser.getFullName().isBlank() || isLegacyCorruptedName(adminUser.getFullName())) {
            adminUser.setFullName(DEFAULT_ADMIN_NAME);
        }
        userRepository.save(adminUser);
    }

    private void ensureNotificationTable(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet tables = metaData.getTables(null, null, "notifications", new String[]{"TABLE"})) {
                if (tables.next()) {
                    return;
                }
            }

            String dbProduct = metaData.getDatabaseProductName().toLowerCase();
            String createSql;
            if (dbProduct.contains("mysql")) {
                createSql = "CREATE TABLE notifications ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "user_id BIGINT NOT NULL, "
                        + "title VARCHAR(200) NOT NULL, "
                        + "content VARCHAR(1000) NOT NULL, "
                        + "type VARCHAR(20) NOT NULL DEFAULT 'info', "
                        + "`read` TINYINT(1) NOT NULL DEFAULT 0, "
                        + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "INDEX idx_user_id (user_id), "
                        + "INDEX idx_user_read (user_id, `read`)"
                        + ")";
            } else {
                createSql = "CREATE TABLE notifications ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "user_id BIGINT NOT NULL, "
                        + "title VARCHAR(200) NOT NULL, "
                        + "content VARCHAR(1000) NOT NULL, "
                        + "type VARCHAR(20) NOT NULL DEFAULT 'info', "
                        + "\"read\" NUMBER(1,0) DEFAULT 0 NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL"
                        + ")";
            }

            try (var stmt = conn.createStatement()) {
                stmt.execute(createSql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create notifications table", e);
        }
    }

    private void seedNotifications(NotificationRepository notificationRepository, UserRepository userRepository) {
        User admin = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).orElse(null);
        if (admin == null) {
            return;
        }
        if (notificationRepository.countByUserIdAndReadFalse(admin.getId()) > 0) {
            return; // already seeded
        }

        Notification n1 = new Notification();
        n1.setUserId(admin.getId());
        n1.setTitle("欢迎登录 GovCMS");
        n1.setContent("欢迎使用 GovCMS 政府内容管理系统。");
        n1.setType("info");
        n1.setRead(false);
        notificationRepository.save(n1);

        Notification n2 = new Notification();
        n2.setUserId(admin.getId());
        n2.setTitle("您的文章已通过审核");
        n2.setContent("您提交的文章已由审核员通过，请前往发布中心执行发布。");
        n2.setType("info");
        n2.setRead(false);
        notificationRepository.save(n2);
    }

    private boolean isLegacyCorruptedName(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(ch -> ch == '?');
    }

    private void upsertRole(Role role, RoleRepository roleRepository, String name, String code, String description, int sort, Set<Permission> permissions) {
        role.setName(name);
        role.setCode(code);
        role.setDescription(description);
        role.setStatus("enabled");
        role.setSort(sort);
        role.setPermissions(permissions);
        roleRepository.save(role);
    }

    private Permission createPermission(String id, String name, String code, String type,
                                        String parentId, String path, String icon, int sort) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setName(name);
        permission.setCode(code);
        permission.setType(type);
        permission.setParentId(parentId);
        permission.setPath(path);
        permission.setIcon(icon);
        permission.setSort(sort);
        return permission;
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
        menu.setMenuGroup("");
        menu.setGroupTitle("");
        return menu;
    }
}


