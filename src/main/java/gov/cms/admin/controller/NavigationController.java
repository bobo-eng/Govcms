package gov.cms.admin.controller;

import gov.cms.admin.dto.NavigationItemRequest;
import gov.cms.admin.dto.NavigationItemSortRequest;
import gov.cms.admin.entity.NavigationItem;
import gov.cms.admin.service.NavigationService;
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
@RequestMapping("/api/navigation")
@CrossOrigin(origins = "*")
public class NavigationController {

    private final NavigationService navigationService;

    public NavigationController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('navigation:manage:view')")
    public ResponseEntity<List<NavigationItem>> getNavigationItems(@RequestParam Long siteId,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) String status) {
        return ResponseEntity.ok(navigationService.getNavigationItems(siteId, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('navigation:manage:view')")
    public ResponseEntity<NavigationItem> getNavigationItem(@PathVariable Long id, @RequestParam Long siteId) {
        return ResponseEntity.ok(navigationService.getNavigationItemById(id, siteId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('navigation:manage:create')")
    public ResponseEntity<NavigationItem> createNavigationItem(@RequestBody NavigationItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(navigationService.createNavigationItem(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('navigation:manage:update')")
    public ResponseEntity<NavigationItem> updateNavigationItem(@PathVariable Long id, @RequestBody NavigationItemRequest request) {
        return ResponseEntity.ok(navigationService.updateNavigationItem(id, request));
    }

    @PutMapping("/{id}/sort")
    @PreAuthorize("hasAuthority('navigation:manage:update')")
    public ResponseEntity<NavigationItem> updateSort(@PathVariable Long id, @RequestBody NavigationItemSortRequest request) {
        return ResponseEntity.ok(navigationService.updateSort(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('navigation:manage:delete')")
    public ResponseEntity<Void> deleteNavigationItem(@PathVariable Long id, @RequestParam Long siteId) {
        navigationService.deleteNavigationItem(id, siteId);
        return ResponseEntity.noContent().build();
    }
}
