package gov.cms.admin.service;

import gov.cms.admin.entity.Role;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.RoleRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserService userService;

    @Test
    void assignRolesRejectsSiteAdminWithoutManagedSite() {
        User user = new User();
        user.setId(1L);
        Role role = new Role();
        role.setId(10L);
        role.setCode("site_admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(10L))).thenReturn(List.of(role));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> userService.assignRoles(1L, Set.of(10L)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void assignRolesClearsManagedSiteWhenSiteAdminRemoved() {
        User user = new User();
        user.setId(1L);
        user.setManagedSiteId(2L);
        Role role = new Role();
        role.setId(11L);
        role.setCode("publisher");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(11L))).thenReturn(List.of(role));
        when(userRepository.save(user)).thenReturn(user);

        User saved = userService.assignRoles(1L, Set.of(11L));

        assertNull(saved.getManagedSiteId());
        assertEquals(1, saved.getRoles().size());
    }

    @Test
    void updateUserRejectsUnknownManagedSite() {
        User existing = new User();
        existing.setId(1L);
        existing.setUsername("demo");
        existing.setEmail("demo@test.local");
        User update = new User();
        update.setManagedSiteId(99L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(siteRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> userService.updateUser(1L, update));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
