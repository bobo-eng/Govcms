package gov.cms.admin.service;

import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.RoleRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SiteRepository siteRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SiteRepository siteRepository,
                       RoleRepository roleRepository,
                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.siteRepository = siteRepository;
        this.roleRepository = roleRepository;
        this.auditLogService = auditLogService;
    }

    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public Page<User> searchUsers(String keyword, Boolean enabled, Pageable pageable) {
        return userRepository.searchUsers(keyword, enabled, pageable);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    @Transactional
    public User createUser(User user) {
        validateCreateUser(user);
        validateManagedSiteId(user.getManagedSiteId());

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
        }

        user.setId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(new LinkedHashSet<>());
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, User updateRequest) {
        User existingUser = getUserById(id);

        if (updateRequest.getUsername() != null) {
            if (updateRequest.getUsername().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
            }
            if (userRepository.existsByUsernameAndIdNot(updateRequest.getUsername(), id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
            }
            existingUser.setUsername(updateRequest.getUsername().trim());
        }

        if (updateRequest.getEmail() != null) {
            if (updateRequest.getEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
            }
            if (userRepository.existsByEmailAndIdNot(updateRequest.getEmail(), id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
            }
            existingUser.setEmail(updateRequest.getEmail().trim());
        }

        if (updateRequest.getPassword() != null) {
            if (updateRequest.getPassword().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
            }
            existingUser.setPassword(passwordEncoder.encode(updateRequest.getPassword()));
        }

        if (updateRequest.getFullName() != null) {
            existingUser.setFullName(updateRequest.getFullName().trim());
        }

        if (updateRequest.getEnabled() != null) {
            existingUser.setEnabled(updateRequest.getEnabled());
        }

        if (updateRequest.getManagedSiteId() != null || existingUser.getManagedSiteId() != null) {
            validateManagedSiteId(updateRequest.getManagedSiteId());
            existingUser.setManagedSiteId(updateRequest.getManagedSiteId());
        }

        return userRepository.save(existingUser);
    }

    @Transactional
    public User assignRoles(Long id, Set<Long> roleIds) {
        User user = getUserById(id);
        Set<Long> normalizedRoleIds = roleIds == null ? Set.of() : roleIds;
        Set<Role> roles = new LinkedHashSet<>(roleRepository.findAllById(normalizedRoleIds));
        if (roles.size() != normalizedRoleIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在");
        }

        boolean hadSiteAdmin = user.getRoles().stream().anyMatch(role -> "site_admin".equals(role.getCode()));
        boolean hasSiteAdmin = roles.stream().anyMatch(role -> "site_admin".equals(role.getCode()));
        if (hasSiteAdmin && user.getManagedSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点管理员必须绑定一个管理站点");
        }
        if (!hasSiteAdmin) {
            user.setManagedSiteId(null);
        }

        user.setRoles(roles);
        User saved = userRepository.save(user);
        if (hasSiteAdmin) {
            auditLogService.record("site_admin_assignment", "user", saved.getId(), saved.getManagedSiteId(), "success", "Assigned site_admin to user", null, null);
        } else if (hadSiteAdmin) {
            auditLogService.record("site_admin_unbind", "user", saved.getId(), null, "success", "Removed site_admin from user", null, null);
        }
        return saved;
    }

    public Set<Long> getRoleIds(Long id) {
        return getUserById(id).getRoles().stream().map(Role::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        userRepository.deleteById(id);
    }

    private void validateCreateUser(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
    }

    private void validateManagedSiteId(Long managedSiteId) {
        if (managedSiteId == null) {
            return;
        }
        if (!siteRepository.existsById(managedSiteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理站点不存在");
        }
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        User user = getUserById(id);
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id) {
        User user = getUserById(id);
        user.setPassword(passwordEncoder.encode("GovCMS@2026"));
        userRepository.save(user);
    }
}
