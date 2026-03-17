package gov.cms.admin.service;

import gov.cms.admin.dto.MenuTreeNode;
import gov.cms.admin.entity.Menu;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.MenuRepository;
import gov.cms.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock private MenuRepository menuRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private MenuService menuService;

    @Test
    void getUserMenusReturnsOnlyAuthorizedMenusWithParents() {
        Permission contentPermission = permission("content");
        Permission reviewPermission = permission("content:article:review");

        Role reviewer = new Role();
        reviewer.setCode("reviewer");
        reviewer.setPermissions(new LinkedHashSet<>(Set.of(contentPermission, reviewPermission)));

        User user = new User();
        user.setUsername("reviewer.uat");
        user.setRoles(new LinkedHashSet<>(Set.of(reviewer)));

        Menu content = menu(10L, "内容管理", "/content", null, "content");
        Menu review = menu(11L, "审核工作区", "/content/review", 10L, "content:article:review");
        Menu publish = menu(12L, "发布中心", "/content/publish", 10L, "publish:center");
        Menu users = menu(20L, "用户管理", "/users", null, "sys:user");

        when(userRepository.findByUsernameWithRoles("reviewer.uat")).thenReturn(Optional.of(user));
        when(menuRepository.findAllByOrderBySortAscIdAsc()).thenReturn(List.of(content, review, publish, users));

        List<MenuTreeNode> result = menuService.getUserMenus("reviewer.uat");

        assertEquals(1, result.size());
        assertEquals("内容管理", result.get(0).getName());
        assertEquals(1, result.get(0).getChildren().size());
        assertEquals("审核工作区", result.get(0).getChildren().get(0).getName());
        assertFalse(result.get(0).getChildren().stream().anyMatch(item -> "发布中心".equals(item.getName())));
        assertTrue(result.stream().noneMatch(item -> "用户管理".equals(item.getName())));
    }

    private Permission permission(String id) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(id);
        permission.setName(id);
        return permission;
    }

    private Menu menu(Long id, String name, String path, Long parentId, String permissionId) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setName(name);
        menu.setPath(path);
        menu.setParentId(parentId);
        menu.setPermissionId(permissionId);
        menu.setVisible(true);
        menu.setStatus("enabled");
        menu.setSort(id.intValue());
        return menu;
    }
}