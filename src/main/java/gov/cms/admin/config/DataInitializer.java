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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class DataInitializer {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@govcms.local";
    private static final String DEFAULT_ADMIN_NAME = "系统管理员";

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
                createPermission("sys", "系统管理", "sys", "menu", null, "/system", "SettingOutlined", 1),
                createPermission("sys:user", "用户管理", "sys:user", "menu", "sys", "/system/users", "UserOutlined", 1),
                createPermission("sys:user:view", "查看用户", "sys:user:view", "button", "sys:user", null, null, 1),
                createPermission("sys:user:create", "新增用户", "sys:user:create", "button", "sys:user", null, null, 2),
                createPermission("sys:user:update", "编辑用户", "sys:user:update", "button", "sys:user", null, null, 3),
                createPermission("sys:user:delete", "删除用户", "sys:user:delete", "button", "sys:user", null, null, 4),
                createPermission("sys:user:reset-password", "重置密码", "sys:user:reset-password", "button", "sys:user", null, null, 5),

                createPermission("sys:role", "角色管理", "sys:role", "menu", "sys", "/system/roles", "TeamOutlined", 2),
                createPermission("sys:role:view", "查看角色", "sys:role:view", "button", "sys:role", null, null, 1),
                createPermission("sys:role:create", "新增角色", "sys:role:create", "button", "sys:role", null, null, 2),
                createPermission("sys:role:update", "编辑角色", "sys:role:update", "button", "sys:role", null, null, 3),
                createPermission("sys:role:delete", "删除角色", "sys:role:delete", "button", "sys:role", null, null, 4),

                createPermission("sys:permission", "权限管理", "sys:permission", "menu", "sys", "/permissions", "LockOutlined", 3),
                createPermission("sys:permission:view", "查看权限", "sys:permission:view", "button", "sys:permission", null, null, 1),
                createPermission("sys:permission:create", "新增权限", "sys:permission:create", "button", "sys:permission", null, null, 2),
                createPermission("sys:permission:update", "编辑权限", "sys:permission:update", "button", "sys:permission", null, null, 3),
                createPermission("sys:permission:delete", "删除权限", "sys:permission:delete", "button", "sys:permission", null, null, 4),

                createPermission("sys:menu", "菜单管理", "sys:menu", "menu", "sys", "/menus", "MenuOutlined", 4),
                createPermission("sys:menu:view", "查看菜单", "sys:menu:view", "button", "sys:menu", null, null, 1),
                createPermission("sys:menu:create", "新增菜单", "sys:menu:create", "button", "sys:menu", null, null, 2),
                createPermission("sys:menu:update", "编辑菜单", "sys:menu:update", "button", "sys:menu", null, null, 3),
                createPermission("sys:menu:delete", "删除菜单", "sys:menu:delete", "button", "sys:menu", null, null, 4),

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

                createPermission("template:manage", "????", "template:manage", "menu", "content", "/content/templates", "LayoutOutlined", 3),
                createPermission("template:manage:view", "????", "template:manage:view", "button", "template:manage", null, null, 1),
                createPermission("template:manage:create", "????", "template:manage:create", "button", "template:manage", null, null, 2),
                createPermission("template:manage:update", "????", "template:manage:update", "button", "template:manage", null, null, 3),
                createPermission("template:manage:bind", "??????", "template:manage:bind", "button", "template:manage", null, null, 4),
                createPermission("template:manage:preview", "????", "template:manage:preview", "button", "template:manage", null, null, 5),
                createPermission("template:manage:delete", "????", "template:manage:delete", "button", "template:manage", null, null, 6),
                createPermission("site", "站点", "site", "menu", null, "/sites", "GlobalOutlined", 3),
                createPermission("site:manage", "站点管理", "site:manage", "menu", "site", "/sites", "GlobalOutlined", 1),
                createPermission("site:manage:view", "查看站点", "site:manage:view", "button", "site:manage", null, null, 1),
                createPermission("site:manage:create", "新增站点", "site:manage:create", "button", "site:manage", null, null, 2),
                createPermission("site:manage:update", "编辑站点", "site:manage:update", "button", "site:manage", null, null, 3),
                createPermission("site:manage:delete", "删除站点", "site:manage:delete", "button", "site:manage", null, null, 4),

                createPermission("media", "媒体", "media", "menu", null, "/media", "CloudOutlined", 4),
                createPermission("media:manage", "媒体管理", "media:manage", "menu", "media", "/media", "CloudOutlined", 1),
                createPermission("media:manage:view", "查看媒体", "media:manage:view", "button", "media:manage", null, null, 1),
                createPermission("media:manage:upload", "上传媒体", "media:manage:upload", "button", "media:manage", null, null, 2),
                createPermission("media:manage:delete", "删除媒体", "media:manage:delete", "button", "media:manage", null, null, 3)
        );
    }

    private void seedRoles(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        List<Permission> permissions = permissionRepository.findAllByOrderBySortAscIdAsc();
        if (permissions.isEmpty()) {
            return;
        }

        upsertRole(
                roleRepository.findByCode("admin").orElseGet(Role::new),
                roleRepository,
                "系统管理员",
                "admin",
                "拥有系统全部权限",
                1,
                new LinkedHashSet<>(permissions)
        );

        Set<Permission> editorPermissions = permissions.stream()
                .filter(permission -> permission.getCode().startsWith("content:"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(
                roleRepository.findByCode("editor").orElseGet(Role::new),
                roleRepository,
                "内容编辑",
                "editor",
                "负责内容编辑与发布",
                2,
                editorPermissions
        );

        Set<Permission> viewerPermissions = permissions.stream()
                .filter(permission -> "content:article:view".equals(permission.getCode()) || "content:category:view".equals(permission.getCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        upsertRole(
                roleRepository.findByCode("viewer").orElseGet(Role::new),
                roleRepository,
                "内容查看",
                "viewer",
                "仅允许查看内容",
                3,
                viewerPermissions
        );
    }

    private void seedMenus(MenuRepository menuRepository) {
        upsertMenu(menuRepository, createMenu("仪表盘", "/dashboard", "DashboardOutlined", null, 1, "content:article:view"), null);
        upsertMenu(menuRepository, createMenu("用户管理", "/users", "UserOutlined", null, 2, "sys:user"), null);
        upsertMenu(menuRepository, createMenu("角色管理", "/roles", "TeamOutlined", null, 3, "sys:role"), null);
        upsertMenu(menuRepository, createMenu("权限管理", "/permissions", "LockOutlined", null, 4, "sys:permission"), null);
        upsertMenu(menuRepository, createMenu("菜单管理", "/menus", "MenuOutlined", null, 5, "sys:menu"), null);

        Menu contentMenu = upsertMenu(menuRepository, createMenu("内容管理", "/content", "FileTextOutlined", null, 6, "content"), null);
        upsertMenu(menuRepository, createMenu("栏目管理", "/content/categories", "FolderOutlined", contentMenu.getId(), 1, "content:category"), contentMenu.getId());
        upsertMenu(menuRepository, createMenu("????", "/content/templates", "LayoutOutlined", contentMenu.getId(), 2, "template:manage"), contentMenu.getId());

        upsertMenu(menuRepository, createMenu("站点管理", "/sites", "GlobalOutlined", null, 7, "site:manage"), null);
        upsertMenu(menuRepository, createMenu("媒体管理", "/media", "CloudOutlined", null, 8, "media:manage"), null);
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
