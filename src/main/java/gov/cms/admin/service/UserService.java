package gov.cms.admin.service;

import gov.cms.admin.entity.User;
import gov.cms.admin.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
        }

        user.setId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
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
            existingUser.setUsername(updateRequest.getUsername());
        }

        if (updateRequest.getEmail() != null) {
            if (updateRequest.getEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
            }
            if (userRepository.existsByEmailAndIdNot(updateRequest.getEmail(), id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
            }
            existingUser.setEmail(updateRequest.getEmail());
        }

        if (updateRequest.getPassword() != null) {
            if (updateRequest.getPassword().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
            }
            existingUser.setPassword(passwordEncoder.encode(updateRequest.getPassword()));
        }

        if (updateRequest.getFullName() != null) {
            existingUser.setFullName(updateRequest.getFullName());
        }

        if (updateRequest.getEnabled() != null) {
            existingUser.setEnabled(updateRequest.getEnabled());
        }

        return userRepository.save(existingUser);
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

    /**
     * 修改用户密码
     */
    @Transactional
    public void changePassword(Long id, String newPassword) {
        User user = getUserById(id);
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 重置用户密码（默认密码：GovCMS@2026）
     */
    @Transactional
    public void resetPassword(Long id) {
        User user = getUserById(id);
        user.setPassword(passwordEncoder.encode("GovCMS@2026"));
        userRepository.save(user);
    }
}
