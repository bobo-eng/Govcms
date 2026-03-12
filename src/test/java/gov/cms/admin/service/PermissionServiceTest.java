package gov.cms.admin.service;

import gov.cms.admin.entity.Permission;
import gov.cms.admin.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    void createPermissionGeneratesIdFromCodeWhenIdMissing() {
        Permission permission = buildPermission();
        permission.setId(null);

        when(permissionRepository.existsById("ui:smoke:perm:0311")).thenReturn(false);
        when(permissionRepository.existsByCodeIgnoreCase("ui:smoke:perm:0311")).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Permission created = permissionService.createPermission(permission);

        assertEquals("ui:smoke:perm:0311", created.getId());
        assertEquals("ui:smoke:perm:0311", created.getCode());
        assertEquals(0, created.getSort());
    }

    @Test
    void createPermissionRejectsDuplicateCode() {
        Permission permission = buildPermission();
        permission.setId(null);

        when(permissionRepository.existsById("ui:smoke:perm:0311")).thenReturn(false);
        when(permissionRepository.existsByCodeIgnoreCase("ui:smoke:perm:0311")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> permissionService.createPermission(permission));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(permissionRepository, never()).save(any(Permission.class));
    }

    @Test
    void updatePermissionRejectsDuplicateCode() {
        Permission existing = buildPermission();
        existing.setId("ui:smoke:perm:0311");

        Permission update = buildPermission();
        update.setCode("ui:smoke:perm:updated");

        when(permissionRepository.findById("ui:smoke:perm:0311")).thenReturn(Optional.of(existing));
        when(permissionRepository.existsByCodeIgnoreCaseAndIdNot("ui:smoke:perm:updated", "ui:smoke:perm:0311")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> permissionService.updatePermission("ui:smoke:perm:0311", update));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(permissionRepository, never()).save(any(Permission.class));
    }

    private Permission buildPermission() {
        Permission permission = new Permission();
        permission.setName("UI 烟测权限");
        permission.setCode("ui:smoke:perm:0311");
        permission.setType("menu");
        permission.setParentId(null);
        permission.setPath("/ui-smoke-permission-0311");
        permission.setIcon("SafetyOutlined");
        permission.setSort(null);
        return permission;
    }
}