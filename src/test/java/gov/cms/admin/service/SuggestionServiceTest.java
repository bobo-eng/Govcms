package gov.cms.admin.service;

import gov.cms.admin.dto.SearchSuggestionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.connection.RedisZSetCommands;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        suggestionService = new SuggestionService(redisTemplate);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void suggestWithKeywordReturnsTitleMatches() {
        String titleKey = "suggest:titles:1";
        Set<String> titleMatches = new LinkedHashSet<>();
        titleMatches.add("政务公开");

        when(zSetOperations.rangeByLex(eq(titleKey), any(RedisZSetCommands.Range.class)))
                .thenReturn(titleMatches);
        when(zSetOperations.score(titleKey, "政务公开")).thenReturn(10.0);

        List<SearchSuggestionItem> result = suggestionService.suggest(1L, "政", 8);

        assertEquals(1, result.size());
        assertEquals("政务公开", result.get(0).getKeyword());
        assertEquals("title", result.get(0).getSource());
        assertEquals(10L, result.get(0).getCount());
    }

    @Test
    void suggestWithEmptyKeywordReturnsPopularQueries() {
        String queryKey = "suggest:queries:1";

        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn("热门搜索");
        when(tuple.getScore()).thenReturn(5.0);

        Set<ZSetOperations.TypedTuple<String>> hotQueries = new LinkedHashSet<>();
        hotQueries.add(tuple);

        when(zSetOperations.reverseRangeWithScores(queryKey, 0, 7)).thenReturn(hotQueries);

        List<SearchSuggestionItem> result = suggestionService.suggest(1L, "", 8);

        assertEquals(1, result.size());
        assertEquals("热门搜索", result.get(0).getKeyword());
        assertEquals("popular", result.get(0).getSource());
        assertEquals(5L, result.get(0).getCount());
    }

    @Test
    void suggestFallsBackToPopularWhenTitleMatchesInsufficient() {
        String titleKey = "suggest:titles:1";
        String queryKey = "suggest:queries:1";

        Set<String> titleMatches = new LinkedHashSet<>();
        titleMatches.add("政务");

        when(zSetOperations.rangeByLex(eq(titleKey), any(RedisZSetCommands.Range.class)))
                .thenReturn(titleMatches);
        when(zSetOperations.score(titleKey, "政务")).thenReturn(3.0);

        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn("政策解读");
        when(tuple.getScore()).thenReturn(8.0);

        Set<ZSetOperations.TypedTuple<String>> hotQueries = new LinkedHashSet<>();
        hotQueries.add(tuple);

        when(zSetOperations.reverseRangeWithScores(queryKey, 0, 6)).thenReturn(hotQueries);

        List<SearchSuggestionItem> result = suggestionService.suggest(1L, "政", 8);

        assertEquals(2, result.size());
        assertEquals("政务", result.get(0).getKeyword());
        assertEquals("title", result.get(0).getSource());
        assertEquals("政策解读", result.get(1).getKeyword());
        assertEquals("popular", result.get(1).getSource());
    }

    @Test
    void suggestDeduplicatesTitleAndPopularResults() {
        String titleKey = "suggest:titles:1";
        String queryKey = "suggest:queries:1";

        Set<String> titleMatches = new LinkedHashSet<>();
        titleMatches.add("政务公开");

        when(zSetOperations.rangeByLex(eq(titleKey), any(RedisZSetCommands.Range.class)))
                .thenReturn(titleMatches);
        when(zSetOperations.score(titleKey, "政务公开")).thenReturn(10.0);

        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn("政务公开");
        lenient().when(tuple.getScore()).thenReturn(5.0);

        Set<ZSetOperations.TypedTuple<String>> hotQueries = new LinkedHashSet<>();
        hotQueries.add(tuple);

        when(zSetOperations.reverseRangeWithScores(queryKey, 0, 6)).thenReturn(hotQueries);

        List<SearchSuggestionItem> result = suggestionService.suggest(1L, "政", 8);

        assertEquals(1, result.size());
        assertEquals("政务公开", result.get(0).getKeyword());
    }

    @Test
    void suggestRespectsLimit() {
        String titleKey = "suggest:titles:1";

        Set<String> titleMatches = new LinkedHashSet<>();
        titleMatches.add("a");
        titleMatches.add("b");
        titleMatches.add("c");

        when(zSetOperations.rangeByLex(eq(titleKey), any(RedisZSetCommands.Range.class)))
                .thenReturn(titleMatches);
        when(zSetOperations.score(anyString(), anyString())).thenReturn(1.0);

        List<SearchSuggestionItem> result = suggestionService.suggest(1L, "x", 2);

        assertEquals(2, result.size());
    }

    @Test
    void suggestReturnsEmptyListWhenNoMatches() {
        when(zSetOperations.rangeByLex(anyString(), any(RedisZSetCommands.Range.class)))
                .thenReturn(null);
        when(zSetOperations.reverseRangeWithScores(anyString(), any(Long.class), any(Long.class)))
                .thenReturn(null);

        List<SearchSuggestionItem> result = suggestionService.suggest(1L, "xyz", 8);

        assertTrue(result.isEmpty());
    }

    @Test
    void recordQueryIncrementsScore() {
        String queryKey = "suggest:queries:1";

        suggestionService.recordQuery(1L, "  政务公开  ");

        verify(zSetOperations).incrementScore(queryKey, "政务公开", 1.0);
    }

    @Test
    void recordQueryIgnoresBlankKeyword() {
        suggestionService.recordQuery(1L, "   ");

        verify(zSetOperations, never()).incrementScore(anyString(), anyString(), any(Double.class));
    }
}
