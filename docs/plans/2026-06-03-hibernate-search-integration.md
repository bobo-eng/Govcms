# Hibernate Search (Lucene) Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the fixed JPQL `CASE WHEN` search with professional full-text indexing using Hibernate Search 7.x and embedded Lucene, supporting dynamic multi-field sorting, relevance scoring (BM25), and result highlighting.

**Architecture:** Add Hibernate Search annotations to `SearchIndexEntry`. Use `SearchSession` from Hibernate Search for querying with dynamic sorts and full-text predicates. Keep the existing `SearchIndexEntryRepository` for non-search operations (CRUD, rebuild). The `PortalSearchController` gains `sort` query parameters. Lucene indexes are stored on disk under `./storage/search-indexes/{siteId}`.

**Tech Stack:** Java 17, Spring Boot 3.2, Hibernate ORM 6.4, Hibernate Search 7.1, Lucene 9.x

---

### Task 1: Add Hibernate Search dependencies

**Files:**
- Modify: `pom.xml`

**Step 1: Add dependencies**

Add inside `<dependencies>` after `spring-boot-starter-data-jpa`:

```xml
<dependency>
    <groupId>org.hibernate.search</groupId>
    <artifactId>hibernate-search-mapper-orm</artifactId>
    <version>7.1.0.Final</version>
</dependency>
<dependency>
    <groupId>org.hibernate.search</groupId>
    <artifactId>hibernate-search-backend-lucene</artifactId>
    <version>7.1.0.Final</version>
</dependency>
```

**Step 2: Compile to verify dependency resolution**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: add Hibernate Search 7.1 and Lucene backend dependencies"
```

---

### Task 2: Configure Hibernate Search properties

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: Add Hibernate Search configuration**

Insert under the existing `spring:` block, after `jpa:`:

```yaml
  jpa:
    properties:
      hibernate:
        search:
          backend:
            type: lucene
            directory:
              type: local-filesystem
              root: ./storage/search-indexes
          indexing:
            plan:
              synchronization:
                strategy: sync
```

Note: This is added to the existing `spring.jpa.properties.hibernate` nesting. Ensure correct YAML indentation (4 spaces under `properties:`).

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add Hibernate Search Lucene backend configuration"
```

---

### Task 3: Annotate SearchIndexEntry for indexing

**Files:**
- Modify: `src/main/java/gov/cms/admin/entity/SearchIndexEntry.java`

**Step 1: Add imports and annotations**

Add imports:

```java
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
```

Add class-level annotation:

```java
@Entity
@Table(name = "search_index_entries", indexes = {
        @Index(name = "idx_search_site_type_object", columnList = "siteId, objectType, objectId", unique = true),
        @Index(name = "idx_search_site_status", columnList = "siteId, status")
})
@Indexed
public class SearchIndexEntry {
```

Annotate fields:

```java
    @Column(nullable = false, length = 500)
    @FullTextField(analyzer = "standard")
    @KeywordField(name = "title_sort", sortable = Sortable.YES)
    private String title;

    @Column(length = 2000)
    @FullTextField(analyzer = "standard")
    private String summary;

    @Column(length = 1000)
    @FullTextField(analyzer = "standard")
    private String keywords;

    @Column(nullable = false, length = 40)
    @KeywordField(sortable = Sortable.YES)
    private String objectType;

    @Column(nullable = false, length = 20)
    @KeywordField(sortable = Sortable.YES)
    private String status;

    @Column
    @GenericField(sortable = Sortable.YES)
    private LocalDateTime publishedAt;

    @Column
    @GenericField(sortable = Sortable.YES)
    private Long categoryId;

    @Column(length = 200)
    @FullTextField(analyzer = "standard")
    private String categoryName;

    @Column(length = 200)
    @FullTextField(analyzer = "standard")
    private String topicName;

    @Column(columnDefinition = "TEXT")
    @FullTextField(analyzer = "standard")
    private String searchText;
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/entity/SearchIndexEntry.java
git commit -m "feat: add Hibernate Search annotations to SearchIndexEntry"
```

---

### Task 4: Create HibernateSearchService

**Files:**
- Create: `src/main/java/gov/cms/admin/service/HibernateSearchService.java`

**Step 1: Write search service**

```java
package gov.cms.admin.service;

import gov.cms.admin.dto.PortalSearchItem;
import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.entity.SearchIndexEntry;
import jakarta.persistence.EntityManager;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.SearchSortFactory;
import org.hibernate.search.engine.search.sort.dsl.SortFinalStep;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HibernateSearchService {

    private final EntityManager entityManager;

    public HibernateSearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public PortalSearchResponse search(Long siteId, String keyword, int page, int size,
                                       String objectType, Long categoryId, String sortField, String sortDirection) {
        SearchSession searchSession = Search.session(entityManager);

        SearchResult<SearchIndexEntry> result = searchSession.search(SearchIndexEntry.class)
                .where(f -> {
                    BooleanPredicateClausesStep<?> bool = f.bool()
                            .must(f.match().field("siteId").matching(siteId));

                    if (keyword != null && !keyword.isBlank()) {
                        bool.must(f.match()
                                .fields("title", "summary", "keywords", "searchText", "categoryName", "topicName")
                                .matching(keyword)
                                .fuzzy(1));
                    }

                    if (objectType != null && !objectType.isBlank()) {
                        bool.must(f.match().field("objectType").matching(objectType));
                    }

                    if (categoryId != null) {
                        bool.must(f.match().field("categoryId").matching(categoryId));
                    }

                    return bool;
                })
                .sort(f -> buildSort(f, sortField, sortDirection))
                .fetch(page * size, size);

        List<PortalSearchItem> items = result.hits().stream()
                .map(this::toPortalItem)
                .toList();

        return new PortalSearchResponse(
                items,
                result.total().hitCount(),
                page,
                size,
                (int) Math.ceil((double) result.total().hitCount() / size)
        );
    }

    private SortFinalStep buildSort(SearchSortFactory f, String sortField, String sortDirection) {
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        String field = sortField != null ? sortField : "publishedAt";

        return switch (field.toLowerCase()) {
            case "title" -> f.field("title_sort").order(asc ? org.hibernate.search.engine.search.sort.dsl.SortOrder.ASC : org.hibernate.search.engine.search.sort.dsl.SortOrder.DESC);
            case "score" -> f.score().order(asc ? org.hibernate.search.engine.search.sort.dsl.SortOrder.ASC : org.hibernate.search.engine.search.sort.dsl.SortOrder.DESC);
            default -> f.field("publishedAt").order(asc ? org.hibernate.search.engine.search.sort.dsl.SortOrder.ASC : org.hibernate.search.engine.search.sort.dsl.SortOrder.DESC);
        };
    }

    private PortalSearchItem toPortalItem(SearchIndexEntry entry) {
        return new PortalSearchItem(
                entry.getObjectType(),
                entry.getObjectId(),
                entry.getTitle(),
                entry.getSummary(),
                entry.getPath(),
                entry.getPublishedAt() != null ? entry.getPublishedAt().toString() : null,
                entry.getCategoryName(),
                entry.getTopicName()
        );
    }

    public void rebuildIndex() {
        SearchSession searchSession = Search.session(entityManager);
        searchSession.massIndexer(SearchIndexEntry.class)
                .threadsToLoadObjects(4)
                .startAndWait();
    }
}
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/HibernateSearchService.java
git commit -m "feat: add HibernateSearchService with full-text search and dynamic sorting"
```

---

### Task 5: Update SearchIndexService to delegate search

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/SearchIndexService.java`

**Step 1: Inject HibernateSearchService and modify search method**

Assuming `SearchIndexService` has a `search(Long siteId, String keyword, int page, int size, String type, Long categoryId)` method, replace or wrap it:

```java
private final HibernateSearchService hibernateSearchService;

// Add to constructor

public PortalSearchResponse search(Long siteId, String keyword, int page, int size,
                                     String type, Long categoryId) {
    return search(siteId, keyword, page, size, type, categoryId, "publishedAt", "desc");
}

public PortalSearchResponse search(Long siteId, String keyword, int page, int size,
                                     String type, Long categoryId, String sortField, String sortDirection) {
    return hibernateSearchService.search(siteId, keyword, page, size, type, categoryId, sortField, sortDirection);
}
```

**Step 2: Add rebuild endpoint using MassIndexer**

```java
public void rebuildSearchIndex(Long siteId) {
    hibernateSearchService.rebuildIndex();
}
```

**Step 3: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/service/SearchIndexService.java
git commit -m "feat: delegate portal search to HibernateSearchService with dynamic sort support"
```

---

### Task 6: Update PortalSearchController with sort parameters

**Files:**
- Modify: `src/main/java/gov/cms/admin/controller/PortalSearchController.java`

**Step 1: Add sort parameters to search endpoint**

```java
    @GetMapping
    public ResponseEntity<PortalSearchResponse> search(@RequestParam Long siteId,
                                                       @RequestParam(required = false, defaultValue = "") String keyword,
                                                       @RequestParam(required = false, defaultValue = "0") int page,
                                                       @RequestParam(required = false, defaultValue = "10") int size,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false, defaultValue = "publishedAt") String sort,
                                                       @RequestParam(required = false, defaultValue = "desc") String direction) {
        return ResponseEntity.ok(searchIndexService.search(siteId, keyword, page, size, type, categoryId, sort, direction));
    }
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/PortalSearchController.java
git commit -m "feat: add sortField and sortDirection params to portal search endpoint"
```

---

### Task 7: Update frontend search API and types

**Files:**
- Modify: `frontend/src/api/searchIndex.ts`

**Step 1: Add sort to search interfaces**

```typescript
export interface PortalSearchParams {
  siteId: number
  keyword?: string
  page?: number
  size?: number
  type?: string
  categoryId?: number
  sort?: string
  direction?: 'asc' | 'desc'
}
```

Update `fetchSearchSuggestions` if needed, and add a new portal search function or verify the existing one supports these params.

Since `PortalSearchController` is under `/api/portal/search`, the frontend might not have a direct API for it yet. Add:

```typescript
export const portalSearch = (params: PortalSearchParams) =>
  api.get<PortalSearchResponse>('/portal/search', { params })
```

**Step 2: Commit**

```bash
git add frontend/src/api/searchIndex.ts
git commit -m "feat: add sort params to frontend portal search API"
```

---

### Task 8: Write tests for HibernateSearchService

**Files:**
- Create: `src/test/java/gov/cms/admin/service/HibernateSearchServiceTest.java`

**Step 1: Write integration-style test**

```java
package gov.cms.admin.service;

import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.entity.SearchIndexEntry;
import gov.cms.admin.repository.SearchIndexEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class HibernateSearchServiceTest {

    @Autowired
    private HibernateSearchService hibernateSearchService;

    @Autowired
    private SearchIndexEntryRepository searchIndexEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        SearchIndexEntry entry1 = new SearchIndexEntry();
        entry1.setSiteId(1L);
        entry1.setObjectType("content");
        entry1.setObjectId(1L);
        entry1.setTitle("Government Digital Service");
        entry1.setSummary("Digital transformation initiatives");
        entry1.setPath("/news/1");
        entry1.setStatus("published");
        entry1.setPublishedAt(LocalDateTime.now().minusDays(1));
        searchIndexEntryRepository.save(entry1);

        SearchIndexEntry entry2 = new SearchIndexEntry();
        entry2.setSiteId(1L);
        entry2.setObjectType("content");
        entry2.setObjectId(2L);
        entry2.setTitle("Public Health Update");
        entry2.setSummary("Latest health policies");
        entry2.setPath("/news/2");
        entry2.setStatus("published");
        entry2.setPublishedAt(LocalDateTime.now().minusDays(5));
        searchIndexEntryRepository.save(entry2);

        entityManager.flush();
        hibernateSearchService.rebuildIndex();
    }

    @Test
    void search_byKeyword_shouldReturnResults() {
        PortalSearchResponse response = hibernateSearchService.search(1L, "digital", 0, 10, null, null, null, null);
        assertTrue(response.totalElements() > 0);
    }

    @Test
    void search_sortByTitleAsc_shouldReturnAlphabeticalOrder() {
        PortalSearchResponse response = hibernateSearchService.search(1L, null, 0, 10, null, null, "title", "asc");
        assertEquals(2, response.totalElements());
        assertTrue(response.items().get(0).title().compareTo(response.items().get(1).title()) <= 0);
    }

    @Test
    void search_sortByPublishedAtDesc_shouldReturnNewestFirst() {
        PortalSearchResponse response = hibernateSearchService.search(1L, null, 0, 10, null, null, "publishedAt", "desc");
        assertTrue(response.items().get(0).publishedAt().compareTo(response.items().get(1).publishedAt()) >= 0);
    }
}
```

Note: The `PortalSearchResponse` constructor and fields may differ from the actual DTO. Adjust during execution to match the real `PortalSearchResponse` structure.

**Step 2: Run tests**

Run: `mvn test -Dtest=HibernateSearchServiceTest`
Expected: Tests run: 3, Failures: 0 (after MassIndexer populates the Lucene index)

**Step 3: Commit**

```bash
git add src/test/java/gov/cms/admin/service/HibernateSearchServiceTest.java
git commit -m "test: add Hibernate Search integration tests for full-text search and sorting"
```

---

### Task 9: Full test verification

**Files:**
- N/A (verification task)

**Step 1: Run all backend tests**

Run: `mvn test`
Expected: BUILD SUCCESS. Note: first run may take longer as Hibernate Search initializes Lucene indexes.

**Step 2: Verify Lucene index directory created**

Run: `ls ./storage/search-indexes/`
Expected: Directory exists and contains subdirectories for indexed entities.

**Step 3: Final commit**

```bash
git add -A
git commit -m "feat: integrate Hibernate Search with Lucene for professional portal search and dynamic sorting"
```

---

## 实施完成后检查清单

- [ ] `mvn test` 全量通过
- [ ] `./storage/search-indexes/` 目录存在且包含 Lucene 索引文件
- [ ] `SearchIndexEntry` 已正确标注 `@Indexed` 和 `@FullTextField`
- [ ] `/api/portal/search?sort=title&direction=asc` 返回按标题排序的结果
- [ ] `/api/portal/search?sort=score&direction=desc` 返回按相关性排序的结果
- [ ] `/api/portal/search?keyword=digital` 使用 BM25 相关性评分
- [ ] 重建索引接口（MassIndexer）能成功重建全量索引
- [ ] 数据库从 MySQL 迁移到 KingbaseES 后，搜索功能不受影响（因为索引由 Lucene 管理）

---

**Plan complete and saved to `docs/plans/2026-06-03-hibernate-search-integration.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints.

**Which approach?**
