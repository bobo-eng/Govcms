package gov.cms.admin.service;

import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Build authorities from roles and permissions
        Collection<GrantedAuthority> authorities = buildAuthorities(user.getRoles());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(Boolean.FALSE.equals(user.getEnabled()))
                .authorities(authorities)
                .build();
    }

    private Collection<GrantedAuthority> buildAuthorities(Collection<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return roles.stream()
                .flatMap(role -> {
                    // Add role as authority (with ROLE_ prefix)
                    var authorities = java.util.stream.Stream.of(
                            new SimpleGrantedAuthority("ROLE_" + role.getCode())
                    );
                    
                    // Add all permissions from this role
                    if (role.getPermissions() != null) {
                        var permAuthorities = role.getPermissions().stream()
                                .map(p -> new SimpleGrantedAuthority(p.getCode()));
                        return java.util.stream.Stream.concat(authorities, permAuthorities);
                    }
                    return authorities;
                })
                .collect(Collectors.toList());
    }
}
