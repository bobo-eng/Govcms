package gov.cms.admin.service;

import gov.cms.admin.entity.Site;
import gov.cms.admin.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @InjectMocks
    private SiteService siteService;

    @Test
    void createSiteRejectsDuplicateCode() {
        Site site = buildSite();
        when(siteRepository.existsByCodeIgnoreCase("gov-main")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> siteService.createSite(site));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void createSiteRejectsDuplicateDomain() {
        Site site = buildSite();
        when(siteRepository.existsByCodeIgnoreCase("gov-main")).thenReturn(false);
        when(siteRepository.existsByDomainIgnoreCase("www.example.gov.cn")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> siteService.createSite(site));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void updateSiteRejectsDuplicateCode() {
        Site existing = buildSite();
        existing.setId(1L);

        Site update = buildSite();
        when(siteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(siteRepository.existsByCodeIgnoreCaseAndIdNot("gov-main", 1L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> siteService.updateSite(1L, update));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void deleteSiteRejectsMissingSite() {
        when(siteRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> siteService.deleteSite(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private Site buildSite() {
        Site site = new Site();
        site.setName("Gov Main");
        site.setCode("Gov-Main");
        site.setDomain("www.example.gov.cn");
        site.setOrganizationId(10L);
        site.setDescription("main site");
        site.setStatus("enabled");
        return site;
    }
}
