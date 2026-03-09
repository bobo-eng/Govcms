package gov.cms.admin.controller;

import gov.cms.admin.dto.LoginRequest;
import gov.cms.admin.dto.LoginResponse;
import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.UserRepository;
import gov.cms.admin.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    public AuthController(JwtUtil jwtUtil, AuthenticationManager authenticationManager, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            String token = jwtUtil.generateToken(request.getUsername());

            // Fetch user roles and permissions
            User user = userRepository.findByUsername(request.getUsername()).orElse(null);
            
            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setUsername(request.getUsername());
            response.setMessage("登录成功");
            
            if (user != null && user.getRoles() != null) {
                // Get role codes
                List<String> roles = user.getRoles().stream()
                        .map(Role::getCode)
                        .collect(Collectors.toList());
                response.setRoles(roles);
                
                // Get permission codes from roles
                List<String> permissions = user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(p -> p.getCode())
                        .distinct()
                        .collect(Collectors.toList());
                response.setPermissions(permissions);
            }
            
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(buildFailedResponse());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("ok");
    }

    private LoginResponse buildFailedResponse() {
        LoginResponse response = new LoginResponse();
        response.setMessage("用户名或密码错误");
        return response;
    }
}
