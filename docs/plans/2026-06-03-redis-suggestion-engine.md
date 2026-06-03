# Redis Suggestion Engine Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a professional search suggestion system using Redis Sorted Sets, combining real-time index title prefixes with popular query statistics for low-latency auto-complete.

**Architecture:** `SuggestionIndexerService` listens to content lifecycle events (or is called by `SearchIndexService` on rebuild) to maintain two Redis data structures per site:
- `suggest:titles:{siteId}` — ZSet of index titles (score = published timestamp for recency bias)
- `suggest:queries:{siteId}` — ZSet of user search queries (score = search frequency)

`SuggestionService` queries both structures, merges and deduplicates results, and returns weighted suggestions. The existing `PortalSearchController.suggestions()` endpoint is updated to delegate to `SuggestionService`.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Data Redis, Redis 6+

---

### Task 1: Add Redis dependency

**Files:**
- Modify: `pom.xml`

**Step 1: Add Spring Data Redis starter**

Add inside `<dependencies>` after `spring-boot-starter-quartz` (or after `spring-boot-starter-data-jpa`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Step 2: Compile to verify dependency resolution**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: add Spring Data Redis starter dependency"
```

---

### Task 2: Configure Redis connection

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: Add Redis configuration**

Insert under the existing `spring:` block:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add Redis connection configuration"
```

---

### Task 3: Create SuggestionIndexerService

**Files:**
- Create: `src/main/java/gov/cms/admin/service/SuggestionIndexerService.java`

**Step 1: Write indexer service**

```java
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
                            ? e.getPublishedAt().toEpochSecond(java.time.ZoneOffset.UTC)
                            : System.currentTimeMillis() / 1000.0;
                    return org.springframework.data.redis.core.DefaultTypedTuple.of(
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
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/SuggestionIndexerService.java
git commit -m "feat: add SuggestionIndexerService for Redis title and query indexing"
```

---

### Task 4: Integrate suggestion indexing into content lifecycle

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/SearchIndexService.java`

**Step 1: Inject SuggestionIndexerService**

Add to constructor and fields:

```java
private final SuggestionIndexerService suggestionIndexerService;
```

**Step 2: Index on rebuild and individual entry changes**

In the methods that rebuild site/category/content/topic indexes, after saving `SearchIndexEntry` entities, call:

```java
suggestionIndexerService.indexTitlesBulk(siteId, savedEntries);
```

In the method that deletes or updates a single entry, call:

```java
suggestionIndexerService.indexTitle(siteId, newEntry.getTitle());
// or
suggestionIndexerService.removeTitle(siteId, oldTitle);
```

Note: The exact method names in `SearchIndexService` may vary. The implementing agent should locate the `rebuildSearchIndexSite`, `rebuildSearchIndexContent`, etc. methods and insert the `suggestionIndexerService` calls after the `SearchIndexEntry` save/delete operations.

**Step 3: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/service/SearchIndexService.java
git commit -m "feat: integrate suggestion indexing into search index rebuild workflow"
```

---

### Task 5: Create SuggestionService

**Files:**
- Create: `src/main/java/gov/cms/admin/service/SuggestionService.java`

**Step 1: Write suggestion service**

```java
package gov.cms.admin.service;

import gov.cms.admin.dto.SearchSuggestionItem;
import org.springframework.data.redis.core.StringRedisTemplate;
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

        Set<SearchSuggestionItem> merged = new LinkedHashSet<>();

        // 1. Title prefix matches (most relevant)
        if (!prefix.isEmpty()) {
            String titleKey = String.format(KEY_TITLES, siteId);
            Set<String> titleMatches = redisTemplate.opsForZSet()
                    .rangeByLex(titleKey,
                            org.springframework.data.redis.connection.RedisZSetCommands.Range.range()
                                    .gte(prefix)
                                    .lt(prefix + "￿"));
            if (titleMatches != null) {
                for (String t : titleMatches) {
                    Double score = redisTemplate.opsForZSet().score(titleKey, t);
                    merged.add(new SearchSuggestionItem(t, "title", score != null ? score.longValue() : 0L));
                    if (merged.size() >= limit) break;
                }
            }
        }

        // 2. Popular queries (fill remaining slots)
        int remaining = limit - merged.size();
        if (remaining > 0) {
            String queryKey = String.format(KEY_QUERIES, siteId);
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> hotQueries =
                    redisTemplate.opsForZSet().reverseRangeWithScores(queryKey, 0, remaining - 1);
            if (hotQueries != null) {
                for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : hotQueries) {
                    if (tuple.getValue() != null) {
                        merged.add(new SearchSuggestionItem(tuple.getValue(), "popular",
                                tuple.getScore() != null ? tuple.getScore().longValue() : 0L));
                    }
                }
            }
        }

        return new ArrayList<>(merged).stream().limit(limit).collect(Collectors.toList());
    }

    public void recordQuery(Long siteId, String keyword) {
        if (siteId == null || keyword == null || keyword.isBlank()) return;
        String key = String.format(KEY_QUERIES, siteId);
        redisTemplate.opsForZSet().incrementScore(key, keyword.trim().toLowerCase(), 1.0);
    }
}
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/SuggestionService.java
git commit -m "feat: add SuggestionService with Redis prefix matching and popular query merging"
```

---

### Task 6: Update PortalSearchController suggestions endpoint

**Files:**
- Modify: `src/main/java/gov/cms/admin/controller/PortalSearchController.java`

**Step 1: Inject SuggestionService and update endpoint**

```java
private final SuggestionService suggestionService;

// Add to constructor

@GetMapping("/suggestions")
public ResponseEntity<List<SearchSuggestionItem>> suggestions(@RequestParam Long siteId,
                                                              @RequestParam(required = false, defaultValue = "") String keyword,
                                                              @RequestParam(required = false, defaultValue = "8") int limit) {
    return ResponseEntity.ok(suggestionService.suggest(siteId, keyword, limit));
}
```

**Step 2: Record queries on actual search**

In the `search()` method of `PortalSearchController`, after calling `searchIndexService.search()`, add:

```java
if (keyword != null && !keyword.isBlank()) {
    suggestionService.recordQuery(siteId, keyword);
}
```

**Step 3: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/PortalSearchController.java
git commit -m "feat: delegate suggestions to Redis SuggestionService and record search queries"
```

---

### Task 7: Update frontend search suggestion API

**Files:**
- Modify: `frontend/src/api/searchIndex.ts`

**Step 1: Verify fetchSearchSuggestions signature**

Ensure the existing function matches the backend endpoint:

```typescript
export const fetchSearchSuggestions = (siteId: number, keyword = '', limit = 8) =>
  api.get<SearchSuggestionItem[]>('/portal/search/suggestions', { params: { siteId, keyword, limit } })
```

This should already be correct. Add a new source type if needed:

```typescript
export interface SearchSuggestionItem {
  keyword: string
  source: 'title' | 'popular'
  count?: number | null
}
```

**Step 2: Commit**

```bash
git add frontend/src/api/searchIndex.ts
git commit -m "feat: update suggestion types to include title source"
```

---

### Task 8: Write tests for SuggestionService

**Files:**
- Create: `src/test/java/gov/cms/admin/service/SuggestionServiceTest.java`

**Step 1: Write test with Mockito**

```java
package gov.cms.admin.service;

import gov.cms.admin.dto.SearchSuggestionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuggestionServiceTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOps;
    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        suggestionService = new SuggestionService(redisTemplate);
    }

    @Test
    void suggest_withPrefix_shouldReturnTitleMatches() {
        when(zSetOps.rangeByLex(anyString(), any()))
                .thenReturn(Set.of("digital government", "digital service"));
        when(zSetOps.score(anyString(), eq("digital government"))).thenReturn(1000.0);
        when(zSetOps.score(anyString(), eq("digital service"))).thenReturn(900.0);

        List<SearchSuggestionItem> results = suggestionService.suggest(1L, "digital", 5);

        assertEquals(2, results.size());
        assertEquals("digital government", results.get(0).keyword());
    }

    @Test
    void recordQuery_shouldIncrementRedisZSet() {
        suggestionService.recordQuery(1L, "health policy");
        verify(zSetOps).incrementScore("suggest:queries:1", "health policy", 1.0);
    }
}
```

Note: `SearchSuggestionItem` is a record. Adjust the test to use getter methods or field access based on the actual DTO definition.

**Step 2: Run tests**

Run: `mvn test -Dtest=SuggestionServiceTest`
Expected: Tests run: 2, Failures: 0

**Step 3: Commit**

```bash
git add src/test/java/gov/cms/admin/service/SuggestionServiceTest.java
git commit -m "test: add unit tests for Redis SuggestionService"
```

---

### Task 9: Full test verification

**Files:**
- N/A (verification task)

**Step 1: Run all backend tests**

Run: `mvn test`
Expected: BUILD SUCCESS

**Step 2: Verify Redis connectivity**
Ensure Redis is running locally (`redis-server`), or configure `REDIS_HOST` environment variable for test profile.

**Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete Redis suggestion engine with title prefix and popular query merging"
```

---

## 实施完成后检查清单

- [ ] `mvn test` 全量通过
- [ ] Redis 服务可连接，Spring Boot 启动无 Redis 连接错误
- [ ] 发布/更新文章后，Redis `suggest:titles:{siteId}` 中新增对应标题
- [ ] 执行搜索后，Redis `suggest:queries:{siteId}` 中对应关键词 score +1
- [ ] `/api/portal/search/suggestions?siteId=1&keyword=dig&limit=5` 返回以 "dig" 开头的标题建议和热门查询合并结果
- [ ] 重建站点索引后，该站点 Redis 建议索引与数据库内容一致
- [ ] 前端 `SearchSuggestionItem.source` 支持 `title` 和 `popular` 两种类型

---

**Plan complete and saved to `docs/plans/2026-06-03-redis-suggestion-engine.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints.

**Which approach?**
