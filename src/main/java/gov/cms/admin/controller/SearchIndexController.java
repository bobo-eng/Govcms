package gov.cms.admin.controller;

import gov.cms.admin.dto.SearchIndexStatusResponse;
import gov.cms.admin.service.SearchIndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-index")
@CrossOrigin(origins = "*")
public class SearchIndexController {

    private final SearchIndexService searchIndexService;

    public SearchIndexController(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('search:ops:view') or hasAuthority('publish:center:view')")
    public ResponseEntity<SearchIndexStatusResponse> getStatus(@RequestParam Long siteId,
                                                               @RequestParam(required = false, defaultValue = "10") int limit,
                                                               @RequestParam(required = false, defaultValue = "7") int days) {
        return ResponseEntity.ok(searchIndexService.getStatus(siteId, limit, days));
    }

    @PostMapping("/rebuild/site/{siteId}")
    @PreAuthorize("hasAuthority('search:ops:rebuild') or hasAuthority('publish:center:execute')")
    public ResponseEntity<SearchIndexStatusResponse> rebuildSite(@PathVariable Long siteId,
                                                                 @RequestParam(required = false, defaultValue = "10") int limit,
                                                                 @RequestParam(required = false, defaultValue = "7") int days) {
        searchIndexService.rebuildSiteIndexForAdmin(siteId);
        return ResponseEntity.ok(searchIndexService.getStatus(siteId, limit, days));
    }

    @PostMapping("/rebuild/content/{articleId}")
    @PreAuthorize("hasAuthority('search:ops:rebuild') or hasAuthority('publish:center:execute')")
    public ResponseEntity<SearchIndexStatusResponse> rebuildContent(@PathVariable Long articleId,
                                                                    @RequestParam(required = false, defaultValue = "10") int limit,
                                                                    @RequestParam(required = false, defaultValue = "7") int days) {
        Long siteId = searchIndexService.rebuildContentIndexForAdmin(articleId);
        return ResponseEntity.ok(searchIndexService.getStatus(siteId, limit, days));
    }

    @PostMapping("/rebuild/topic/{topicId}")
    @PreAuthorize("hasAuthority('search:ops:rebuild') or hasAuthority('publish:center:execute')")
    public ResponseEntity<SearchIndexStatusResponse> rebuildTopic(@PathVariable Long topicId,
                                                                  @RequestParam(required = false, defaultValue = "10") int limit,
                                                                  @RequestParam(required = false, defaultValue = "7") int days) {
        Long siteId = searchIndexService.rebuildTopicIndexForAdmin(topicId);
        return ResponseEntity.ok(searchIndexService.getStatus(siteId, limit, days));
    }

    @PostMapping("/rebuild/category/{categoryId}")
    @PreAuthorize("hasAuthority('search:ops:rebuild') or hasAuthority('publish:center:execute')")
    public ResponseEntity<SearchIndexStatusResponse> rebuildCategory(@PathVariable Long categoryId,
                                                                     @RequestParam(required = false, defaultValue = "10") int limit,
                                                                     @RequestParam(required = false, defaultValue = "7") int days) {
        Long siteId = searchIndexService.rebuildCategoryIndexForAdmin(categoryId);
        return ResponseEntity.ok(searchIndexService.getStatus(siteId, limit, days));
    }
}
