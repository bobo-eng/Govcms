package gov.cms.admin.service;

import gov.cms.admin.entity.Permission;
import gov.cms.admin.entity.Role;
import gov.cms.admin.repository.PermissionRepository;
import gov.cms.admin.repository.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAllByOrderBySortAscIdAsc();
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
    }

    @Transactional
    public Role createRole(Role role) {
        if (roleRepository.existsByCode(role.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色编码已存在");
        }
        if (role.getPermissions() == null) {
            role.setPermissions(new HashSet<>());
        }
        if (role.getStatus() == null) {
            role.setStatus("enabled");
        }
        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, Role roleData) {
        Role role = getRoleById(id);
        
        if (roleRepository.existsByCodeAndIdNot(roleData.getCode(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色编码已存在");
        }
        
        role.setName(roleData.getName());
        role.setCode(roleData.getCode());
        role.setDescription(roleData.getDescription());
        role.setStatus(roleData.getStatus());
        role.setSort(roleData.getSort());
        
        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = getRoleById(id);
        roleRepository.delete(role);
    }

    @Transactional
    public Role assignPermissions(Long roleId, Set<String> permissionIds) {
        Role role = getRoleById(roleId);
        
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.setPermissions(permissions);
        
        return roleRepository.save(role);
    }
}
