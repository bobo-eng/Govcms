package gov.cms.admin.service;

import gov.cms.admin.dto.CurrentSiteResponse;
import gov.cms.admin.dto.SiteOptionDto;
import gov.cms.admin.entity.Site;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SiteService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";

    private final SiteRepository siteRepository;
    private final SiteAccessService siteAccessService;
    private final UserRepository userRepository;

    public SiteService(SiteRepository siteRepository, SiteAccessService siteAccessService, UserRepository userRepository) {
        this.siteRepository = siteRepository;
        this.siteAccessService = siteAccessService;
        this.userRepository = userRepository;
    }

    public Page<Site> getSites(String keyword, String status, Long organizationId, Pageable pageable) {
        if (!siteAccessService.isScopedSiteAdmin()) {
            return siteRepository.searchSites(keyword, normalizeStatus(status, true), organizationId, pageable);
        }
        Site site = resolveCurrentManagedSite();
        return new PageImpl<>(List.of(site), pageable, 1);
    }

    public List<SiteOptionDto> getSiteOptions() {
        if (siteAccessService.isScopedSiteAdmin()) {
            Site site = resolveCurrentManagedSite();
            return List.of(new SiteOptionDto(site.getId(), site.getName(), site.getStatus()));
        }
        return siteRepository.findAll().stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .map(site -> new SiteOptionDto(site.getId(), site.getName(), site.getStatus()))
                .toList();
    }

    public CurrentSiteResponse getCurrentManagedSite() {
        Site site = resolveCurrentManagedSite();
        CurrentSiteResponse response = new CurrentSiteResponse();
        response.setId(site.getId());
        response.setName(site.getName());
        response.setCode(site.getCode());
        response.setDomain(site.getDomain());
        response.setOrganizationId(site.getOrganizationId());
        response.setDescription(site.getDescription());
        response.setStatus(site.getStatus());
        return response;
    }

    public Site getSiteById(Long id) {
        if (siteAccessService.isScopedSiteAdmin()) {
            siteAccessService.assertAccessibleSite(id);
        }
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found."));
    }

    @Transactional
    public Site createSite(Site site) {
        prepareForSave(site);

        if (siteRepository.existsByCodeIgnoreCase(site.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Site code already exists.");
        }
        if (site.getDomain() != null && siteRepository.existsByDomainIgnoreCase(site.getDomain())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Site domain already exists.");
        }

        return siteRepository.save(site);
    }

    @Transactional
    public Site updateSite(Long id, Site siteData) {
        if (siteAccessService.isScopedSiteAdmin()) {
            siteAccessService.assertAccessibleSite(id);
        }
        Site site = getSiteById(id);
        prepareForSave(siteData);

        if (siteRepository.existsByCodeIgnoreCaseAndIdNot(siteData.getCode(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Site code already exists.");
        }
        if (siteData.getDomain() != null && siteRepository.existsByDomainIgnoreCaseAndIdNot(siteData.getDomain(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Site domain already exists.");
        }
        if (STATUS_DISABLED.equals(siteData.getStatus()) && userRepository.existsByManagedSiteId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前站点仍绑定了站点管理员，无法禁用");
        }

        site.setName(siteData.getName());
        site.setCode(siteData.getCode());
        site.setDomain(siteData.getDomain());
        site.setOrganizationId(siteData.getOrganizationId());
        site.setDescription(siteData.getDescription());
        site.setStatus(siteData.getStatus());
        return siteRepository.save(site);
    }

    @Transactional
    public void deleteSite(Long id) {
        if (siteAccessService.isScopedSiteAdmin()) {
            siteAccessService.assertAccessibleSite(id);
        }
        if (userRepository.existsByManagedSiteId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前站点仍绑定了站点管理员，无法删除");
        }
        Site site = getSiteById(id);
        siteRepository.delete(site);
    }

    private Site resolveCurrentManagedSite() {
        Long siteId = siteAccessService.getCurrentManagedSiteId();
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found."));
    }

    private void prepareForSave(Site site) {
        if (site == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site payload is required.");
        }

        String name = normalizeText(site.getName());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site name is required.");
        }

        String code = normalizeCode(site.getCode());
        if (code == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site code is required.");
        }

        String domain = normalizeText(site.getDomain());
        String status = normalizeStatus(site.getStatus(), false);

        site.setName(name);
        site.setCode(code);
        site.setDomain(domain);
        site.setStatus(status);
        site.setDescription(normalizeText(site.getDescription()));
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site code format is invalid.");
        }
        return normalized;
    }

    private String normalizeStatus(String value, boolean allowNull) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            return STATUS_ENABLED;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!STATUS_ENABLED.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site status is invalid.");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
