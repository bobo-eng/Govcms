package gov.cms.admin.service;

import gov.cms.admin.dto.PermissionTreeNode;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.repository.PermissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderBySortAscIdAsc();
    }

    public List<PermissionTreeNode> getPermissionTree() {
        List<Permission> allPermissions = getAllPermissions();
        
        // Build tree structure
        Map<String, PermissionTreeNode> nodeMap = allPermissions.stream()
            .collect(Collectors.toMap(
                Permission::getId,
                PermissionTreeNode::from
            ));

        List<PermissionTreeNode> roots = new ArrayList<>();
        
        for (Permission permission : allPermissions) {
            PermissionTreeNode node = nodeMap.get(permission.getId());
            String parentId = permission.getParentId();
            
            if (parentId == null || "".equals(parentId)) {
                roots.add(node);
            } else {
                PermissionTreeNode parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        
        return roots;
    }

    public Permission getPermissionById(String id) {
        return permissionRepository.findById(id).orElse(null);
    }

    @Transactional
    public Permission createPermission(Permission permission) {
        // Check if ID already exists
        if (permissionRepository.existsById(permission.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "权限ID已存在");
        }
        
        // Set defaults
        if (permission.getSort() == null) {
            permission.setSort(0);
        }
        if (permission.getType() == null) {
            permission.setType("menu");
        }
        
        return permissionRepository.save(permission);
    }

    @Transactional
    public Permission updatePermission(String id, Permission permissionData) {
        Permission permission = permissionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "权限不存在"));
        
        permission.setName(permissionData.getName());
        permission.setCode(permissionData.getCode());
        permission.setType(permissionData.getType());
        permission.setParentId(permissionData.getParentId());
        permission.setPath(permissionData.getPath());
        permission.setIcon(permissionData.getIcon());
        permission.setSort(permissionData.getSort());
        
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(String id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "权限不存在");
        }
        permissionRepository.deleteById(id);
    }
}
