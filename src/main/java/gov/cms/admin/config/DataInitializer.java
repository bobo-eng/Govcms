package gov.cms.admin.config;

import gov.cms.admin.entity.Menu;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.MenuRepository;
import gov.cms.admin.repository.PermissionRepository;
import gov.cms.admin.repository.RoleRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private static final String DEFAULT_ADMIN_NAME = "?????";

    @Bean
    public CommandLineRunner initData(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            MenuRepository menuRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            seedPermissions(permissionRepository);
            seedRoles(permissionRepository, roleRepository);
            seedMenus(menuRepository);
            seedDefaultAdmin(userRepository, roleRepository, passwordEncoder);
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
                createPermission("sys", "\u7cfb\u7edf\u7ba1\u7406", "sys", "menu", null, "/system", "SettingOutlined", 1),
                createPermission("sys:user", "\u7528\u6237\u7ba1\u7406", "sys:user", "menu", "sys", "/users", "UserOutlined", 1),
                createPermission("sys:user:view", "????", "sys:user:view", "button", "sys:user", null, null, 1),
                createPermission("sys:user:create", "????", "sys:user:create", "button", "sys:user", null, null, 2),
                createPermission("sys:user:update", "????", "sys:user:update", "button", "sys:user", null, null, 3),
                createPermission("sys:user:delete", "????", "sys:user:delete", "button", "sys:user", null, null, 4),
                createPermission("sys:user:reset-password", "????", "sys:user:reset-password", "button", "sys:user", null, null, 5),

                createPermission("sys:role", "\u89d2\u8272\u7ba1\u7406", "sys:role", "menu", "sys", "/roles", "TeamOutlined", 2),
                createPermission("sys:role:view", "????", "sys:role:view", "button", "sys:role", null, null, 1),
                createPermission("sys:role:create", "????", "sys:role:create", "button", "sys:role", null, null, 2),
                createPermission("sys:role:update", "????", "sys:role:update", "button", "sys:role", null, null, 3),
                createPermission("sys:role:delete", "????", "sys:role:delete", "button", "sys:role", null, null, 4),

                createPermission("sys:permission", "\u6743\u9650\u7ba1\u7406", "sys:permission", "menu", "sys", "/permissions", "LockOutlined", 3),
                createPermission("sys:permission:view", "????", "sys:permission:view", "button", "sys:permission", null, null, 1),
                createPermission("sys:permission:create", "????", "sys:permission:create", "button", "sys:permission", null, null, 2),
                createPermission("sys:permission:update", "????", "sys:permission:update", "button", "sys:permission", null, null, 3),
                createPermission("sys:permission:delete", "????", "sys:permission:delete", "button", "sys:permission", null, null, 4),

                createPermission("sys:menu", "\u83dc\u5355\u7ba1\u7406", "sys:menu", "menu", "sys", "/menus", "MenuOutlined", 4),
                createPermission("sys:menu:view", "????", "sys:menu:view", "button", "sys:menu", null, null, 1),
                createPermission("sys:menu:create", "????", "sys:menu:create", "button", "sys:menu", null, null, 2),
                createPermission("sys:menu:update", "????", "sys:menu:update", "button", "sys:menu", null, null, 3),
                createPermission("sys:menu:delete", "????", "sys:menu:delete", "button", "sys:menu", null, null, 4),

                createPermission("content", "\u5185\u5bb9\u7ba1\u7406", "content", "menu", null, "/content", "FileTextOutlined", 2),
                createPermission("content:article", "\u5185\u5bb9\u7ba1\u7406", "content:article", "menu", "content", "/content", "FileTextOutlined", 1),
                createPermission("content:article:view", "????", "content:article:view", "button", "content:article", null, null, 1),
                createPermission("content:article:create", "????", "content:article:create", "button", "content:article", null, null, 2),
                createPermission("content:article:update", "????", "content:article:update", "button", "content:article", null, null, 3),
                createPermission("content:article:delete", "????", "content:article:delete", "button", "content:article", null, null, 4),
                createPermission("content:article:submit-review", "????", "content:article:submit-review", "button", "content:article", null, null, 5),
                createPermission("content:article:review", "????", "content:article:review", "button", "content:article", null, null, 6),
                createPermission("content:article:reject", "????", "content:article:reject", "button", "content:article", null, null, 7),
                createPermission("content:article:offline", "????", "content:article:offline", "button", "content:article", null, null, 8),
                createPermission("content:article:history:view", "??????", "content:article:history:view", "button", "content:article", null, null, 9),

                createPermission("content:category", "\u680f\u76ee\u7ba1\u7406", "content:category", "menu", "content", "/content/categories", "FolderOutlined", 2),
                createPermission("content:category:view", "????", "content:category:view", "button", "content:category", null, null, 1),
                createPermission("content:category:create", "????", "content:category:create", "button", "content:category", null, null, 2),
                createPermission("content:category:update", "????", "content:category:update", "button", "content:category", null, null, 3),
                createPermission("content:category:delete", "????", "content:category:delete", "button", "content:category", null, null, 4),

                createPermission("template:manage", "\u6a21\u677f\u7ba1\u7406", "template:manage", "menu", "content", "/content/templates", "LayoutOutlined", 3),
                createPermission("template:manage:view", "????", "template:manage:view", "button", "template:manage", null, null, 1),
                createPermission("template:manage:create", "????", "template:manage:create", "button", "template:manage", null, null, 2),
                createPermission("template:manage:update", "????", "template:manage:update", "button", "template:manage", null, null, 3),
                createPermission("template:manage:bind", "????", "template:manage:bind", "button", "template:manage", null, null, 4),
                createPermission("template:manage:preview", "????", "template:manage:preview", "button", "template:manage", null, null, 5),
                createPermission("template:manage:delete", "????", "template:manage:delete", "button", "template:manage", null, null, 6),

                createPermission("publish:center", "\u53d1\u5e03\u4e2d\u5fc3", "publish:center", "menu", "content", "/content/publish", "SendOutlined", 4),
                createPermission("publish:center:view", "??????", "publish:center:view", "button", "publish:center", null, null, 1),
                createPermission("publish:center:execute", "????", "publish:center:execute", "button", "publish:center", null, null, 2),
                createPermission("publish:center:rollback", "????", "publish:center:rollback", "button", "publish:center", null, null, 3),
                createPermission("publish:center:artifact:view", "??????", "publish:center:artifact:view", "button", "publish:center", null, null, 4),
                createPermission("publish:center:log:view", "??????", "publish:center:log:view", "button", "publish:center", null, null, 5),

                createPermission("site", "\u7ad9\u70b9", "site", "menu", null, "/sites", "GlobalOutlined", 3),
                createPermission("site:manage", "\u7ad9\u70b9\u7ba1\u7406", "site:manage", "menu", "site", "/sites", "GlobalOutlined", 1),
                createPermission("site:manage:view", "????", "site:manage:view", "button", "site:manage", null, null, 1),
                createPermission("site:manage:create", "????", "site:manage:create", "button", "site:manage", null, null, 2),
                createPermission("site:manage:update", "????", "site:manage:update", "button", "site:manage", null, null, 3),
                createPermission("site:manage:delete", "????", "site:manage:delete", "button", "site:manage", null, null, 4),

                createPermission("media", "\u5a92\u4f53", "media", "menu", null, "/media", "CloudOutlined", 4),
                createPermission("media:manage", "\u5a92\u4f53\u7ba1\u7406", "media:manage", "menu", "media", "/media", "CloudOutlined", 1),
                createPermission("media:manage:view", "????", "media:manage:view", "button", "media:manage", null, null, 1),
                createPermission("media:manage:upload", "????", "media:manage:upload", "button", "media:manage", null, null, 2),
                createPermission("media:manage:delete", "????", "media:manage:delete", "button", "media:manage", null, null, 3)
        );
    }

    private void seedRoles(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        List<Permission> permissions = permissionRepository.findAllByOrderBySortAscIdAsc();
        if (permissions.isEmpty()) {
            return;
        }

        upsertRole(roleRepository.findByCode("admin").orElseGet(Role::new), roleRepository,
                "?????", "admin", "????????", 1, new LinkedHashSet<>(permissions));

        Set<Permission> editorPermissions = permissions.stream()
                .filter(permission -> Set.of(
                        "content", "content:article", "content:article:view", "content:article:create", "content:article:update",
                        "content:article:delete", "content:article:submit-review", "content:article:history:view",
                        "content:category", "content:category:view", "template:manage", "template:manage:view", "template:manage:preview"
                ).contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("editor").orElseGet(Role::new), roleRepository,
                "??", "editor", "????????????", 2, editorPermissions);

        Set<Permission> reviewerPermissions = permissions.stream()
                .filter(permission -> Set.of(
                        "content", "content:article:view", "content:article:review", "content:article:reject", "content:article:history:view",
                        "template:manage:view", "template:manage:preview"
                ).contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("reviewer").orElseGet(Role::new), roleRepository,
                "???", "reviewer", "?????????", 3, reviewerPermissions);

        Set<Permission> publisherPermissions = permissions.stream()
                .filter(permission -> permission.getCode().startsWith("publish:center")
                        || Set.of("content:article:view", "content:article:offline", "content:article:history:view", "site:manage:view", "template:manage:view", "content:category:view").contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("publisher").orElseGet(Role::new), roleRepository,
                "???", "publisher", "????????????", 4, publisherPermissions);

        Set<Permission> viewerPermissions = permissions.stream()
                .filter(permission -> Set.of("content:article:view", "content:category:view", "template:manage:view", "site:manage:view", "media:manage:view").contains(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(roleRepository.findByCode("viewer").orElseGet(Role::new), roleRepository,
                "??", "viewer", "??????????", 5, viewerPermissions);
    }

    private void seedMenus(MenuRepository menuRepository) {
        upsertMenu(menuRepository, createMenu("\u4eea\u8868\u76d8", "/dashboard", "DashboardOutlined", null, 1, "content:article:view"), null);
        upsertMenu(menuRepository, createMenu("\u7528\u6237\u7ba1\u7406", "/users", "UserOutlined", null, 2, "sys:user"), null);
        upsertMenu(menuRepository, createMenu("\u89d2\u8272\u7ba1\u7406", "/roles", "TeamOutlined", null, 3, "sys:role"), null);
        upsertMenu(menuRepository, createMenu("\u6743\u9650\u7ba1\u7406", "/permissions", "LockOutlined", null, 4, "sys:permission"), null);
        upsertMenu(menuRepository, createMenu("\u83dc\u5355\u7ba1\u7406", "/menus", "MenuOutlined", null, 5, "sys:menu"), null);

        Menu contentMenu = upsertMenu(menuRepository, createMenu("\u5185\u5bb9\u7ba1\u7406", "/content", "FileTextOutlined", null, 6, "content"), null);
        upsertMenu(menuRepository, createMenu("\u5ba1\u6838\u5de5\u4f5c\u533a", "/content/review", "AuditOutlined", contentMenu.getId(), 1, "content:article:review"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("\u680f\u76ee\u7ba1\u7406", "/content/categories", "FolderOutlined", contentMenu.getId(), 2, "content:category"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("\u6a21\u677f\u7ba1\u7406", "/content/templates", "LayoutOutlined", contentMenu.getId(), 3, "template:manage"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("\u53d1\u5e03\u4e2d\u5fc3", "/content/publish", "SendOutlined", contentMenu.getId(), 4, "publish:center"), contentMenu.getId());

        upsertMenu(menuRepository, createMenu("\u7ad9\u70b9\u7ba1\u7406", "/sites", "GlobalOutlined", null, 7, "site:manage"), null);
        upsertMenu(menuRepository, createMenu("\u5a92\u4f53\u7ba1\u7406", "/media", "CloudOutlined", null, 8, "media:manage"), null);
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