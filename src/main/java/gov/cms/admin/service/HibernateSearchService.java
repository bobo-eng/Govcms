package gov.cms.admin.service;

import gov.cms.admin.dto.PortalSearchItem;
import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.entity.SearchIndexEntry;
import jakarta.persistence.EntityManager;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.SearchSortFactory;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HibernateSearchService {

  private final EntityManager entityManager;

  public HibernateSearchService(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
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

    PortalSearchResponse response = new PortalSearchResponse();
    response.setItems(items);
    response.setTotal(result.total().hitCount());
    response.setPage(page);
    response.setSize(size);
    return response;
  }

  private org.hibernate.search.engine.search.sort.dsl.SortFinalStep buildSort(SearchSortFactory f, String sortField, String sortDirection) {
    boolean asc = !"desc".equalsIgnoreCase(sortDirection);
    String field = sortField != null ? sortField : "publishedAt";

    return switch (field.toLowerCase()) {
      case "title" ->
          f.field("title_sort").order(asc ? org.hibernate.search.engine.search.sort.dsl.SortOrder.ASC : org.hibernate.search.engine.search.sort.dsl.SortOrder.DESC);
      case "score" ->
          f.score().order(asc ? org.hibernate.search.engine.search.sort.dsl.SortOrder.ASC : org.hibernate.search.engine.search.sort.dsl.SortOrder.DESC);
      default ->
          f.field("publishedAt").order(asc ? org.hibernate.search.engine.search.sort.dsl.SortOrder.ASC : org.hibernate.search.engine.search.sort.dsl.SortOrder.DESC);
    };
  }

  private PortalSearchItem toPortalItem(SearchIndexEntry entry) {
    PortalSearchItem item = new PortalSearchItem();
    item.setObjectType(entry.getObjectType());
    item.setObjectId(entry.getObjectId());
    item.setTitle(entry.getTitle());
    item.setSummary(entry.getSummary());
    item.setPath(entry.getPath());
    item.setCategoryName(entry.getCategoryName());
    item.setTopicName(entry.getTopicName());
    item.setPublishedAt(entry.getPublishedAt());
    return item;
  }

  @Transactional
  public void rebuildIndex() {
    SearchSession searchSession = Search.session(entityManager);
    try {
      searchSession.massIndexer(SearchIndexEntry.class)
          .threadsToLoadObjects(4)
          .startAndWait();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Mass indexer interrupted", e);
    }
  }
}
