package gov.cms.admin.controller;

import gov.cms.admin.dto.SiteOptionDto;
import gov.cms.admin.entity.Site;
import gov.cms.admin.service.SiteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
@CrossOrigin(origins = "*")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('site:manage:view')")
    public ResponseEntity<Page<Site>> getSites(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long organizationId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(siteService.getSites(keyword, status, organizationId, pageable));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('content:article:view','publish:center:view','site:manage:view')")
    public ResponseEntity<List<SiteOptionDto>> getSiteOptions() {
        return ResponseEntity.ok(siteService.getSiteOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('site:manage:view')")
    public ResponseEntity<Site> getSite(@PathVariable Long id) {
        return ResponseEntity.ok(siteService.getSiteById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('site:manage:create')")
    public ResponseEntity<Site> createSite(@RequestBody Site site) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createSite(site));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('site:manage:update')")
    public ResponseEntity<Site> updateSite(@PathVariable Long id, @RequestBody Site site) {
        return ResponseEntity.ok(siteService.updateSite(id, site));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('site:manage:delete')")
    public ResponseEntity<Void> deleteSite(@PathVariable Long id) {
        siteService.deleteSite(id);
        return ResponseEntity.noContent().build();
    }
}