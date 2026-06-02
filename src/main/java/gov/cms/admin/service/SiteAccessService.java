package gov.cms.admin.service;

import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SiteAccessService {

    private final SiteRepository siteRepository;
    private final UserRepository userRepository;

    public SiteAccessService(SiteRepository siteRepository, UserRepository userRepository) {
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
    }

    public boolean isAdmin() {
        return hasAuthority("ROLE_admin");
    }

    public boolean isSiteAdmin() {
        return hasAuthority("ROLE_site_admin");
    }

    public boolean isScopedSiteAdmin() {
        return isSiteAdmin() && !isAdmin();
    }

    public Long getCurrentManagedSiteId() {
        User user = getCurrentUser();
        if (user.getManagedSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managed site is not assigned for current user.");
        }
        Site site = siteRepository.findById(user.getManagedSiteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Managed site not found."));
        return site.getId();
    }

    public Long resolveAccessibleSiteId(Long requestedSiteId) {
        if (!isScopedSiteAdmin()) {
            if (requestedSiteId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
            }
            return requestedSiteId;
        }
        Long managedSiteId = getCurrentManagedSiteId();
        if (requestedSiteId != null && !managedSiteId.equals(requestedSiteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site access is limited to the managed site.");
        }
        return managedSiteId;
    }

    public void assertAccessibleSite(Long siteId) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        if (isScopedSiteAdmin() && !getCurrentManagedSiteId().equals(siteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site access is limited to the managed site.");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found."));
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
