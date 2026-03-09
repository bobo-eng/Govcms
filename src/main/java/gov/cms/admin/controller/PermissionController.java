package gov.cms.admin.controller;

import gov.cms.admin.dto.PermissionTreeNode;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/permissions")
@CrossOrigin(origins = "*")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public ResponseEntity<List<PermissionTreeNode>> getPermissionTree() {
        return ResponseEntity.ok(permissionService.getPermissionTree());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public ResponseEntity<Permission> getPermissionById(@PathVariable String id) {
        Permission permission = permissionService.getPermissionById(id);
        if (permission == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(permission);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:permission:create')")
    public ResponseEntity<Permission> createPermission(@RequestBody Permission permission) {
        Permission created = permissionService.createPermission(permission);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:update')")
    public ResponseEntity<Permission> updatePermission(@PathVariable String id, @RequestBody Permission permission) {
        Permission updated = permissionService.updatePermission(id, permission);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:delete')")
    public ResponseEntity<Void> deletePermission(@PathVariable String id) {
        permissionService.deletePermission(id);
        return ResponseEntity.ok().build();
    }
}
