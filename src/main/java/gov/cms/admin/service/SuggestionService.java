package gov.cms.admin.service;

import gov.cms.admin.dto.SearchSuggestionItem;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private static final String KEY_TITLES = "suggest:titles:%s";
    private static final String KEY_QUERIES = "suggest:queries:%s";

    private final StringRedisTemplate redisTemplate;

    public SuggestionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<SearchSuggestionItem> suggest(Long siteId, String keyword, int limit) {
        if (keyword == null) keyword = "";
        String prefix = keyword.trim().toLowerCase();

        List<SearchSuggestionItem> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (!prefix.isEmpty()) {
            String titleKey = String.format(KEY_TITLES, siteId);
            Set<String> titleMatches = redisTemplate.opsForZSet()
                    .rangeByLex(titleKey,
                            org.springframework.data.redis.connection.RedisZSetCommands.Range.range()
                                    .gte(prefix)
                                    .lt(prefix + "￿"));
            if (titleMatches != null) {
                for (String t : titleMatches) {
                    if (seen.add(t)) {
                        Double score = redisTemplate.opsForZSet().score(titleKey, t);
                        merged.add(new SearchSuggestionItem(t, "title", score != null ? score.longValue() : 0L));
                        if (merged.size() >= limit) break;
                    }
                }
            }
        }

        int remaining = limit - merged.size();
        if (remaining > 0) {
            String queryKey = String.format(KEY_QUERIES, siteId);
            Set<ZSetOperations.TypedTuple<String>> hotQueries =
                    redisTemplate.opsForZSet().reverseRangeWithScores(queryKey, 0, remaining - 1);
            if (hotQueries != null) {
                for (ZSetOperations.TypedTuple<String> tuple : hotQueries) {
                    String value = tuple.getValue();
                    if (value != null && seen.add(value)) {
                        merged.add(new SearchSuggestionItem(value, "popular",
                                tuple.getScore() != null ? tuple.getScore().longValue() : 0L));
                    }
                }
            }
        }

        return merged.stream().limit(limit).collect(Collectors.toList());
    }

    public void recordQuery(Long siteId, String keyword) {
        if (siteId == null || keyword == null || keyword.isBlank()) return;
        String key = String.format(KEY_QUERIES, siteId);
        redisTemplate.opsForZSet().incrementScore(key, keyword.trim().toLowerCase(), 1.0);
    }
}
