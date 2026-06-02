package gov.cms.admin.service;

import gov.cms.admin.dto.SearchKeywordStatItem;
import gov.cms.admin.dto.SearchSuggestionItem;
import gov.cms.admin.entity.SearchQueryLog;
import gov.cms.admin.repository.SearchQueryLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchQueryLogServiceTest {

    @Mock private SearchQueryLogRepository searchQueryLogRepository;

    @InjectMocks private SearchQueryLogService searchQueryLogService;

    @Test
    void recordPersistsQueryLog() {
        searchQueryLogService.record(1L, "政务", "content", 9L, 3);

        verify(searchQueryLogRepository).save(any(SearchQueryLog.class));
    }

    @Test
    void listHotKeywordsReadsRepository() {
        when(searchQueryLogRepository.findHotKeywords(eq(1L), any(LocalDateTime.class), eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(new SearchKeywordStatItem("政务", 4)));

        List<SearchKeywordStatItem> result = searchQueryLogService.listHotKeywords(1L, 5, 7);

        assertEquals(1, result.size());
        assertEquals("政务", result.get(0).getKeyword());
    }

    @Test
    void listLowResultKeywordsReadsRepository() {
        when(searchQueryLogRepository.findLowResultKeywords(eq(1L), any(LocalDateTime.class), eq(3L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(new SearchKeywordStatItem("政策", 3)));

        List<SearchKeywordStatItem> result = searchQueryLogService.listLowResultKeywords(1L, 10, 7, 3);

        assertEquals(1, result.size());
        assertEquals("政策", result.get(0).getKeyword());
    }

    @Test
    void listPopularSuggestionsReadsRepository() {
        when(searchQueryLogRepository.findPopularSuggestions(eq(1L), any(LocalDateTime.class), eq("政"), eq(PageRequest.of(0, 8))))
                .thenReturn(List.of(new SearchKeywordStatItem("政务公开", 6)));

        List<SearchSuggestionItem> result = searchQueryLogService.listPopularSuggestions(1L, "政", 8, 7);

        assertEquals(1, result.size());
        assertEquals("popular", result.get(0).getSource());
        assertEquals("政务公开", result.get(0).getKeyword());
    }
}
