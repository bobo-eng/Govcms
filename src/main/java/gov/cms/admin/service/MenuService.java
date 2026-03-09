package gov.cms.admin.service;

import gov.cms.admin.dto.MenuTreeNode;
import gov.cms.admin.entity.Menu;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.MenuRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final MenuRepository menuRepository;
    private final UserRepository userRepository;

    public MenuService(MenuRepository menuRepository, UserRepository userRepository) {
        this.menuRepository = menuRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get all menus (tree structure)
     */
    public List<MenuTreeNode> getAllMenus() {
        List<Menu> allMenus = menuRepository.findAllByOrderBySortAscIdAsc();
        return buildMenuTree(allMenus);
    }

    /**
     * Get user-specific menus based on permissions
     * Returns child menus with groupTitle from their parent
     */
    @Transactional(readOnly = true)
    public List<MenuTreeNode> getUserMenus(String username) {
        // Get user with roles and permissions (eager fetch)
        User user = userRepository.findByUsernameWithRoles(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        // Collect permission IDs from user's roles
        Set<String> permissionIds = new HashSet<>();
        if (user.getRoles() != null) {
            for (var role : user.getRoles()) {
                if (role.getPermissions() != null) {
                    for (Permission perm : role.getPermissions()) {
                        permissionIds.add(perm.getId());
                    }
                }
            }
        }

        // Get all menus
        List<Menu> allMenus = menuRepository.findAllByOrderBySortAscIdAsc();
        
        // Get all menus (including parents for tree structure)
        // Filter: has path OR is a parent menu (parentId is null OR 0)
        List<Menu> userMenus = allMenus.stream()
                .filter(m -> (m.getPath() != null && !m.getPath().isEmpty()) 
                          || m.getParentId() == null 
                          || m.getParentId() == 0L)
                .collect(Collectors.toList());
        
        // Set groupTitle for child menus from parent
        // Also treat parentId=0 as null (top-level menu)
        Map<Long, Menu> parentMap = userMenus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0L)
                .collect(Collectors.toMap(Menu::getId, m -> m));
        
        userMenus.forEach(menu -> {
            if (menu.getParentId() != null && parentMap.containsKey(menu.getParentId())) {
                Menu parent = parentMap.get(menu.getParentId());
                menu.setGroupTitle(parent.getName());
            }
        });

        return buildMenuTree(userMenus);
    }

    /**
     * Build menu tree from flat list
     */
    private List<MenuTreeNode> buildMenuTree(List<Menu> menus) {
        Map<Long, MenuTreeNode> nodeMap = menus.stream()
                .collect(Collectors.toMap(Menu::getId, MenuTreeNode::from));

        List<MenuTreeNode> roots = new ArrayList<>();

        for (Menu menu : menus) {
            MenuTreeNode node = nodeMap.get(menu.getId());
            Long parentId = menu.getParentId();
            
            // Treat parentId=0 as null (top-level menu)
            if (parentId == null || parentId == 0L) {
                roots.add(node);
            } else {
                MenuTreeNode parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        return roots;
    }

    public Menu getMenuById(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在"));
    }

    @Transactional
    public Menu createMenu(Menu menu) {
        if (menu.getSort() == null) {
            menu.setSort(0);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(true);
        }
        if (menu.getStatus() == null) {
            menu.setStatus("enabled");
        }
        return menuRepository.save(menu);
    }

    @Transactional
    public Menu updateMenu(Long id, Menu menuData) {
        Menu menu = getMenuById(id);
        menu.setName(menuData.getName());
        menu.setPath(menuData.getPath());
        menu.setIcon(menuData.getIcon());
        menu.setParentId(menuData.getParentId());
        menu.setSort(menuData.getSort());
        menu.setPermissionId(menuData.getPermissionId());
        menu.setVisible(menuData.getVisible());
        menu.setStatus(menuData.getStatus());
        menu.setMenuGroup(menuData.getMenuGroup());
        menu.setGroupTitle(menuData.getGroupTitle());
        return menuRepository.save(menu);
    }

    @Transactional
    public void deleteMenu(Long id) {
        Menu menu = getMenuById(id);
        menuRepository.delete(menu);
    }
}
