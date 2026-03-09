package gov.cms.admin.controller;

import gov.cms.admin.dto.MenuTreeNode;
import gov.cms.admin.entity.Menu;
import gov.cms.admin.security.JwtUtil;
import gov.cms.admin.service.MenuService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@CrossOrigin(origins = "*")
public class MenuController {

    private final MenuService menuService;
    private final JwtUtil jwtUtil;

    public MenuController(MenuService menuService, JwtUtil jwtUtil) {
        this.menuService = menuService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Get all menus (admin view)
     */
    @GetMapping
    public ResponseEntity<List<MenuTreeNode>> getAllMenus() {
        return ResponseEntity.ok(menuService.getAllMenus());
    }

    /**
     * Get current user's menus based on permissions
     */
    @GetMapping("/user")
    public ResponseEntity<List<MenuTreeNode>> getUserMenus() {
        // Get current username from security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return ResponseEntity.ok(menuService.getUserMenus(username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Menu> getMenuById(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getMenuById(id));
    }

    @PostMapping
    public ResponseEntity<Menu> createMenu(@RequestBody Menu menu) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createMenu(menu));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Menu> updateMenu(@PathVariable Long id, @RequestBody Menu menu) {
        return ResponseEntity.ok(menuService.updateMenu(id, menu));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }
}
