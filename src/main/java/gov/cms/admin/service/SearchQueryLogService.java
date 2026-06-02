package gov.cms.admin.service;

import gov.cms.admin.dto.SearchKeywordStatItem;
import gov.cms.admin.dto.SearchSuggestionItem;
import gov.cms.admin.entity.SearchQueryLog;
import gov.cms.admin.repository.SearchQueryLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SearchQueryLogService {

    private final SearchQueryLogRepository searchQueryLogRepository;

    public SearchQueryLogService(SearchQueryLogRepository searchQueryLogRepository) {
        this.searchQueryLogRepository = searchQueryLogRepository;
    }

    public void record(Long siteId, String keyword, String type, Long categoryId, long resultCount) {
        if (siteId == null) {
            return;
        }
        SearchQueryLog log = new SearchQueryLog();
        log.setSiteId(siteId);
        log.setKeyword(keyword == null ? "" : keyword.trim());
        log.setType(type);
        log.setCategoryId(categoryId);
        log.setResultCount(resultCount);
        searchQueryLogRepository.save(log);
    }

    public List<SearchKeywordStatItem> listHotKeywords(Long siteId, int limit, int days) {
        return searchQueryLogRepository.findHotKeywords(siteId, resolveSince(days), PageRequest.of(0, Math.max(1, limit)));
    }

    public List<SearchKeywordStatItem> listZeroResultKeywords(Long siteId, int limit, int days) {
        return searchQueryLogRepository.findZeroResultKeywords(siteId, resolveSince(days), PageRequest.of(0, Math.max(1, limit)));
    }

    public List<SearchKeywordStatItem> listLowResultKeywords(Long siteId, int limit, int days, long maxResults) {
        return searchQueryLogRepository.findLowResultKeywords(siteId, resolveSince(days), maxResults, PageRequest.of(0, Math.max(1, limit)));
    }

    public List<SearchSuggestionItem> listPopularSuggestions(Long siteId, String keyword, int limit, int days) {
        String prefix = keyword == null ? "" : keyword.trim();
        return searchQueryLogRepository.findPopularSuggestions(siteId, resolveSince(days), prefix, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(item -> new SearchSuggestionItem(item.getKeyword(), "popular", item.getCount()))
                .toList();
    }

    private LocalDateTime resolveSince(int days) {
        return LocalDateTime.now().minusDays(Math.max(1, days));
    }
}
