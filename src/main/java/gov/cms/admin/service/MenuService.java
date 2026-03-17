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
import java.util.HashMap;
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

    public List<MenuTreeNode> getAllMenus() {
        return buildMenuTree(menuRepository.findAllByOrderBySortAscIdAsc());
    }

    @Transactional(readOnly = true)
    public List<MenuTreeNode> getUserMenus(String username) {
        User user = userRepository.findByUsernameWithRoles(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        Set<String> permissionIds = new HashSet<>();
        if (user.getRoles() != null) {
            for (var role : user.getRoles()) {
                if (role.getPermissions() != null) {
                    for (Permission permission : role.getPermissions()) {
                        permissionIds.add(permission.getId());
                    }
                }
            }
        }

        List<Menu> allMenus = menuRepository.findAllByOrderBySortAscIdAsc();
        Map<Long, Menu> menuIndex = allMenus.stream().collect(Collectors.toMap(Menu::getId, menu -> menu));
        Set<Long> includedIds = new HashSet<>();

        for (Menu menu : allMenus) {
            if (!Boolean.TRUE.equals(menu.getVisible()) || !"enabled".equalsIgnoreCase(menu.getStatus())) {
                continue;
            }
            String permissionId = menu.getPermissionId();
            if (permissionId != null && permissionIds.contains(permissionId)) {
                includeWithAncestors(menu, menuIndex, includedIds);
            }
        }

        List<Menu> userMenus = allMenus.stream()
                .filter(menu -> includedIds.contains(menu.getId()))
                .toList();

        Map<Long, Menu> parentMap = userMenus.stream()
                .filter(menu -> menu.getParentId() == null || menu.getParentId() == 0L)
                .collect(Collectors.toMap(Menu::getId, menu -> menu));

        userMenus.forEach(menu -> {
            if (menu.getParentId() != null && parentMap.containsKey(menu.getParentId())) {
                menu.setGroupTitle(parentMap.get(menu.getParentId()).getName());
            }
        });

        return buildMenuTree(userMenus);
    }

    private void includeWithAncestors(Menu menu, Map<Long, Menu> menuIndex, Set<Long> includedIds) {
        Menu cursor = menu;
        while (cursor != null && includedIds.add(cursor.getId())) {
            Long parentId = cursor.getParentId();
            if (parentId == null || parentId == 0L) {
                break;
            }
            cursor = menuIndex.get(parentId);
        }
    }

    private List<MenuTreeNode> buildMenuTree(List<Menu> menus) {
        Map<Long, MenuTreeNode> nodeMap = new HashMap<>();
        for (Menu menu : menus) {
            nodeMap.put(menu.getId(), MenuTreeNode.from(menu));
        }

        List<MenuTreeNode> roots = new ArrayList<>();
        for (Menu menu : menus) {
            MenuTreeNode node = nodeMap.get(menu.getId());
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0L || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }
        return roots;
    }

    public Menu getMenuById(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found."));
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
        if (!menuRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found.");
        }
        menuRepository.deleteById(id);
    }
}