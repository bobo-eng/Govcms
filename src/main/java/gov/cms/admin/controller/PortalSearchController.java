package gov.cms.admin.controller;

import gov.cms.admin.dto.PortalSearchCategoryItem;
import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.dto.SearchSuggestionItem;
import gov.cms.admin.service.SearchIndexService;
import gov.cms.admin.service.SuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal/search")
@CrossOrigin(origins = "*")
public class PortalSearchController {

    private final SearchIndexService searchIndexService;
    private final SuggestionService suggestionService;

    public PortalSearchController(SearchIndexService searchIndexService, SuggestionService suggestionService) {
        this.searchIndexService = searchIndexService;
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public ResponseEntity<PortalSearchResponse> search(@RequestParam Long siteId,
                                                       @RequestParam(required = false, defaultValue = "") String keyword,
                                                       @RequestParam(required = false, defaultValue = "0") int page,
                                                       @RequestParam(required = false, defaultValue = "10") int size,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false, defaultValue = "publishedAt") String sort,
                                                       @RequestParam(required = false, defaultValue = "desc") String direction) {
        PortalSearchResponse response = searchIndexService.search(siteId, keyword, page, size, type, categoryId, sort, direction);
        if (keyword != null && !keyword.isBlank()) {
            suggestionService.recordQuery(siteId, keyword);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SearchSuggestionItem>> suggestions(@RequestParam Long siteId,
                                                                  @RequestParam(required = false, defaultValue = "") String keyword,
                                                                  @RequestParam(required = false, defaultValue = "8") int limit) {
        return ResponseEntity.ok(suggestionService.suggest(siteId, keyword, limit));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<PortalSearchCategoryItem>> listCategories(@RequestParam Long siteId) {
        return ResponseEntity.ok(searchIndexService.listCategories(siteId));
    }
}
