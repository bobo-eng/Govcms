package gov.cms.admin.service;

import gov.cms.admin.entity.SearchIndexEntry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SuggestionIndexerService {

    private static final String KEY_TITLES = "suggest:titles:%s";
    private static final String KEY_QUERIES = "suggest:queries:%s";

    private final StringRedisTemplate redisTemplate;

    public SuggestionIndexerService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void indexTitle(Long siteId, String title) {
        if (title == null || title.isBlank()) return;
        String key = String.format(KEY_TITLES, siteId);
        double score = System.currentTimeMillis() / 1000.0;
        redisTemplate.opsForZSet().add(key, title.trim().toLowerCase(), score);
    }

    public void removeTitle(Long siteId, String title) {
        if (title == null || title.isBlank()) return;
        String key = String.format(KEY_TITLES, siteId);
        redisTemplate.opsForZSet().remove(key, title.trim().toLowerCase());
    }

    public void indexTitlesBulk(Long siteId, List<SearchIndexEntry> entries) {
        String key = String.format(KEY_TITLES, siteId);
        Set<ZSetOperations.TypedTuple<String>> tuples = entries.stream()
                .filter(e -> e.getTitle() != null && !e.getTitle().isBlank())
                .map(e -> {
                    double score = e.getPublishedAt() != null
                            ? e.getPublishedAt().toEpochSecond(ZoneOffset.UTC)
                            : System.currentTimeMillis() / 1000.0;
                    return new org.springframework.data.redis.core.DefaultTypedTuple<>(
                            e.getTitle().trim().toLowerCase(), score);
                })
                .collect(Collectors.toSet());
        if (!tuples.isEmpty()) {
            redisTemplate.opsForZSet().add(key, tuples);
        }
    }

    public void recordQuery(Long siteId, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        String key = String.format(KEY_QUERIES, siteId);
        redisTemplate.opsForZSet().incrementScore(key, keyword.trim().toLowerCase(), 1.0);
    }

    public void clearSiteIndex(Long siteId) {
        redisTemplate.delete(String.format(KEY_TITLES, siteId));
        redisTemplate.delete(String.format(KEY_QUERIES, siteId));
    }
}
