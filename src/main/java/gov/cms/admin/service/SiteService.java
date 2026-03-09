package gov.cms.admin.service;

import gov.cms.admin.entity.Site;
import gov.cms.admin.repository.SiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SiteService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";

    private final SiteRepository siteRepository;

    public SiteService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Page<Site> getSites(String keyword, String status, Long organizationId, Pageable pageable) {
        return siteRepository.searchSites(keyword, normalizeStatus(status, true), organizationId, pageable);
    }

    public Site getSiteById(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "站点不存在"));
    }

    @Transactional
    public Site createSite(Site site) {
        prepareForSave(site);

        if (siteRepository.existsByCodeIgnoreCase(site.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "站点编码已存在");
        }
        if (site.getDomain() != null && siteRepository.existsByDomainIgnoreCase(site.getDomain())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "站点域名已存在");
        }

        return siteRepository.save(site);
    }

    @Transactional
    public Site updateSite(Long id, Site siteData) {
        Site site = getSiteById(id);
        prepareForSave(siteData);

        if (siteRepository.existsByCodeIgnoreCaseAndIdNot(siteData.getCode(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "站点编码已存在");
        }
        if (siteData.getDomain() != null && siteRepository.existsByDomainIgnoreCaseAndIdNot(siteData.getDomain(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "站点域名已存在");
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
        Site site = getSiteById(id);
        siteRepository.delete(site);
    }

    private void prepareForSave(Site site) {
        if (site == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点数据不能为空");
        }

        String name = normalizeText(site.getName());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点名称不能为空");
        }

        String code = normalizeCode(site.getCode());
        if (code == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点编码不能为空");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点编码格式不正确");
        }

        site.setName(name);
        site.setCode(code);
        site.setDomain(normalizeDomain(site.getDomain()));
        site.setDescription(normalizeText(site.getDescription()));
        site.setStatus(normalizeStatus(site.getStatus(), false));
    }

    private String normalizeCode(String code) {
        String normalized = normalizeText(code);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeDomain(String domain) {
        String normalized = normalizeText(domain);
        if (normalized == null) {
            return null;
        }
        if (normalized.contains("://") || normalized.contains("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点域名不能包含协议或路径");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status, boolean allowNull) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            return STATUS_ENABLED;
        }
        if (!STATUS_ENABLED.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点状态不正确");
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
