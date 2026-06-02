package gov.cms.admin.service;

import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteAccessServiceTest {

    @Mock private SiteRepository siteRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SiteAccessService siteAccessService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveAccessibleSiteIdReturnsManagedSiteForScopedSiteAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "site-admin", "pwd", List.of(new SimpleGrantedAuthority("ROLE_site_admin"))
        ));
        User user = new User();
        user.setUsername("site-admin");
        user.setManagedSiteId(2L);
        Site site = new Site();
        site.setId(2L);
        when(userRepository.findByUsername("site-admin")).thenReturn(Optional.of(user));
        when(siteRepository.findById(2L)).thenReturn(Optional.of(site));

        assertEquals(2L, siteAccessService.resolveAccessibleSiteId(null));
        assertEquals(2L, siteAccessService.resolveAccessibleSiteId(2L));
    }

    @Test
    void resolveAccessibleSiteIdRejectsCrossSiteAccessForScopedSiteAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "site-admin", "pwd", List.of(new SimpleGrantedAuthority("ROLE_site_admin"))
        ));
        User user = new User();
        user.setUsername("site-admin");
        user.setManagedSiteId(2L);
        Site site = new Site();
        site.setId(2L);
        when(userRepository.findByUsername("site-admin")).thenReturn(Optional.of(user));
        when(siteRepository.findById(2L)).thenReturn(Optional.of(site));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> siteAccessService.resolveAccessibleSiteId(3L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void getCurrentManagedSiteIdRejectsMissingBinding() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "site-admin", "pwd", List.of(new SimpleGrantedAuthority("ROLE_site_admin"))
        ));
        User user = new User();
        user.setUsername("site-admin");
        when(userRepository.findByUsername("site-admin")).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, siteAccessService::getCurrentManagedSiteId);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
}
