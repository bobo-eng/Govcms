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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private static final int MAX_PERMISSION_ID_LENGTH = 50;

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAllByOrderBySortAscIdAsc();
    }

    public List<PermissionTreeNode> getPermissionTree() {
        List<Permission> allPermissions = getAllPermissions();

        Map<String, PermissionTreeNode> nodeMap = allPermissions.stream()
                .collect(Collectors.toMap(
                        Permission::getId,
                        PermissionTreeNode::from
                ));

        List<PermissionTreeNode> roots = new ArrayList<>();

        for (Permission permission : allPermissions) {
            PermissionTreeNode node = nodeMap.get(permission.getId());
            String parentId = permission.getParentId();

            if (parentId == null || parentId.isBlank()) {
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
        ensurePermissionPayload(permission);

        String code = normalizeRequired(permission.getCode(), "权限编码不能为空");
        String permissionId = resolvePermissionId(permission, code);

        if (permissionRepository.existsById(permissionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "权限ID已存在");
        }
        if (permissionRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "权限编码已存在");
        }

        applyNormalizedFields(permission, permission, permissionId);
        return permissionRepository.save(permission);
    }

    @Transactional
    public Permission updatePermission(String id, Permission permissionData) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "权限不存在"));

        ensurePermissionPayload(permissionData);
        String code = normalizeRequired(permissionData.getCode(), "权限编码不能为空");
        if (permissionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "权限编码已存在");
        }

        applyNormalizedFields(permission, permissionData, id);
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(String id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "权限不存在");
        }
        permissionRepository.deleteById(id);
    }

    private void applyNormalizedFields(Permission target, Permission source, String resolvedId) {
        String normalizedName = normalizeRequired(source.getName(), "权限名称不能为空");
        String normalizedCode = normalizeRequired(source.getCode(), "权限编码不能为空");
        String normalizedType = normalizeType(source.getType());
        String normalizedParentId = normalizeNullable(source.getParentId());

        validatePermissionId(resolvedId);
        validateParentId(resolvedId, normalizedParentId);

        target.setId(resolvedId);
        target.setName(normalizedName);
        target.setCode(normalizedCode);
        target.setType(normalizedType);
        target.setParentId(normalizedParentId);
        target.setPath(normalizeNullable(source.getPath()));
        target.setIcon(normalizeNullable(source.getIcon()));
        target.setSort(source.getSort() == null ? 0 : source.getSort());
    }

    private void ensurePermissionPayload(Permission permission) {
        if (permission == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "权限数据不能为空");
        }
    }

    private String resolvePermissionId(Permission permission, String normalizedCode) {
        String providedId = normalizeNullable(permission.getId());
        return providedId != null ? providedId : normalizedCode;
    }

    private void validatePermissionId(String permissionId) {
        if (permissionId.length() > MAX_PERMISSION_ID_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "权限编码过长，请缩短后重试");
        }
    }

    private void validateParentId(String permissionId, String parentId) {
        if (parentId == null) {
            return;
        }
        if (permissionId.equals(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父级权限不能选择自身");
        }
        if (!permissionRepository.existsById(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父级权限不存在");
        }
    }

    private String normalizeType(String type) {
        String normalizedType = normalizeNullable(type);
        if (normalizedType == null) {
            return "menu";
        }

        String lowerCaseType = normalizedType.toLowerCase(Locale.ROOT);
        return switch (lowerCaseType) {
            case "menu", "button", "api" -> lowerCaseType;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "权限类型不正确");
        };
    }

    private String normalizeRequired(String value, String message) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalizedValue;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}