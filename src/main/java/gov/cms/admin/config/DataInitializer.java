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
                createPermission("sys", "????", "sys", "menu", null, "/system", "SettingOutlined", 1),
                createPermission("sys:user", "????", "sys:user", "menu", "sys", "/system/users", "UserOutlined", 1),
                createPermission("sys:user:view", "????", "sys:user:view", "button", "sys:user", null, null, 1),
                createPermission("sys:user:create", "????", "sys:user:create", "button", "sys:user", null, null, 2),
                createPermission("sys:user:update", "????", "sys:user:update", "button", "sys:user", null, null, 3),
                createPermission("sys:user:delete", "????", "sys:user:delete", "button", "sys:user", null, null, 4),
                createPermission("sys:user:reset-password", "????", "sys:user:reset-password", "button", "sys:user", null, null, 5),

                createPermission("sys:role", "????", "sys:role", "menu", "sys", "/system/roles", "TeamOutlined", 2),
                createPermission("sys:role:view", "????", "sys:role:view", "button", "sys:role", null, null, 1),
                createPermission("sys:role:create", "????", "sys:role:create", "button", "sys:role", null, null, 2),
                createPermission("sys:role:update", "????", "sys:role:update", "button", "sys:role", null, null, 3),
                createPermission("sys:role:delete", "????", "sys:role:delete", "button", "sys:role", null, null, 4),

                createPermission("sys:permission", "????", "sys:permission", "menu", "sys", "/permissions", "LockOutlined", 3),
                createPermission("sys:permission:view", "????", "sys:permission:view", "button", "sys:permission", null, null, 1),
                createPermission("sys:permission:create", "????", "sys:permission:create", "button", "sys:permission", null, null, 2),
                createPermission("sys:permission:update", "????", "sys:permission:update", "button", "sys:permission", null, null, 3),
                createPermission("sys:permission:delete", "????", "sys:permission:delete", "button", "sys:permission", null, null, 4),

                createPermission("sys:menu", "????", "sys:menu", "menu", "sys", "/menus", "MenuOutlined", 4),
                createPermission("sys:menu:view", "????", "sys:menu:view", "button", "sys:menu", null, null, 1),
                createPermission("sys:menu:create", "????", "sys:menu:create", "button", "sys:menu", null, null, 2),
                createPermission("sys:menu:update", "????", "sys:menu:update", "button", "sys:menu", null, null, 3),
                createPermission("sys:menu:delete", "????", "sys:menu:delete", "button", "sys:menu", null, null, 4),

                createPermission("content", "????", "content", "menu", null, "/content", "FileTextOutlined", 2),
                createPermission("content:article", "????", "content:article", "menu", "content", "/content/articles", "FileTextOutlined", 1),
                createPermission("content:article:view", "????", "content:article:view", "button", "content:article", null, null, 1),
                createPermission("content:article:create", "????", "content:article:create", "button", "content:article", null, null, 2),
                createPermission("content:article:update", "????", "content:article:update", "button", "content:article", null, null, 3),
                createPermission("content:article:delete", "????", "content:article:delete", "button", "content:article", null, null, 4),
                createPermission("content:article:publish", "????", "content:article:publish", "button", "content:article", null, null, 5),
                createPermission("content:category", "????", "content:category", "menu", "content", "/content/categories", "FolderOutlined", 2),
                createPermission("content:category:view", "????", "content:category:view", "button", "content:category", null, null, 1),
                createPermission("content:category:create", "????", "content:category:create", "button", "content:category", null, null, 2),
                createPermission("content:category:update", "????", "content:category:update", "button", "content:category", null, null, 3),
                createPermission("content:category:delete", "????", "content:category:delete", "button", "content:category", null, null, 4),

                createPermission("site", "????", "site", "menu", null, "/site", "GlobalOutlined", 3),
                createPermission("site:manage", "????", "site:manage", "menu", "site", "/site/list", "GlobalOutlined", 1),
                createPermission("site:manage:view", "????", "site:manage:view", "button", "site:manage", null, null, 1),
                createPermission("site:manage:create", "????", "site:manage:create", "button", "site:manage", null, null, 2),
                createPermission("site:manage:update", "????", "site:manage:update", "button", "site:manage", null, null, 3),
                createPermission("site:manage:delete", "????", "site:manage:delete", "button", "site:manage", null, null, 4),

                createPermission("media", "????", "media", "menu", null, "/media", "CloudOutlined", 4),
                createPermission("media:manage", "???", "media:manage", "menu", "media", "/media", "CloudOutlined", 1),
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

        Role adminRole = roleRepository.findByCode("admin").orElseGet(Role::new);
        adminRole.setName("?????");
        adminRole.setCode("admin");
        adminRole.setDescription("????????");
        adminRole.setStatus("enabled");
        adminRole.setSort(1);
        adminRole.setPermissions(new LinkedHashSet<>(permissions));
        roleRepository.save(adminRole);

        if (!roleRepository.existsByCode("editor")) {
            Role editorRole = new Role();
            editorRole.setName("????");
            editorRole.setCode("editor");
            editorRole.setDescription("?????????");
            editorRole.setStatus("enabled");
            editorRole.setSort(2);
            Set<Permission> editorPermissions = permissions.stream()
                    .filter(permission -> permission.getCode().startsWith("content:"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            editorRole.setPermissions(editorPermissions);
            roleRepository.save(editorRole);
        }

        if (!roleRepository.existsByCode("viewer")) {
            Role viewerRole = new Role();
            viewerRole.setName("????");
            viewerRole.setCode("viewer");
            viewerRole.setDescription("??????");
            viewerRole.setStatus("enabled");
            viewerRole.setSort(3);
            Set<Permission> viewerPermissions = permissions.stream()
                    .filter(permission -> "content:article:view".equals(permission.getCode()) || "content:category:view".equals(permission.getCode()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            viewerRole.setPermissions(viewerPermissions);
            roleRepository.save(viewerRole);
        }
    }

    private void seedMenus(MenuRepository menuRepository) {
        if (menuRepository.count() > 0) {
            return;
        }

        List<Menu> menus = Arrays.asList(
                createMenu("???", "/dashboard", "DashboardOutlined", null, 1, "content:article:view"),
                createMenu("????", "/users", "UserOutlined", null, 2, "sys:user"),
                createMenu("????", "/roles", "TeamOutlined", null, 3, "sys:role"),
                createMenu("????", "/permissions", "LockOutlined", null, 4, "sys:permission"),
                createMenu("????", "/menus", "MenuOutlined", null, 5, "sys:menu"),
                createMenu("????", "/content", "FileTextOutlined", null, 6, "content"),
                createMenu("????", "/sites", "GlobalOutlined", null, 7, "site:manage"),
                createMenu("????", "/media", "CloudOutlined", null, 8, "media:manage")
        );

        menuRepository.saveAll(menus);
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
            userRepository.save(adminUser);
        }
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
        return menu;
    }
}
