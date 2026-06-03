package gov.cms.admin.service;

import gov.cms.admin.dto.PortalSearchItem;
import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.dto.SearchKeywordStatItem;
import gov.cms.admin.dto.SearchIndexStatusResponse;
import gov.cms.admin.dto.SearchSuggestionItem;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.SearchIndexEntry;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.AuditLogRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SearchIndexEntryRepository;
import gov.cms.admin.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    @Mock private SearchIndexEntryRepository searchIndexEntryRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private SearchQueryLogService searchQueryLogService;
    @Mock private AuditLogService auditLogService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SiteAccessService siteAccessService;
    @Mock private HibernateSearchService hibernateSearchService;

    @InjectMocks private SearchIndexService searchIndexService;

    @Test
    void syncContentEntryCreatesIndexRecord() {
        Article article = new Article();
        article.setId(11L);
        article.setSiteId(1L);
        article.setTitle("新闻标题");
        article.setSummary("新闻摘要");
        article.setContent("正文内容");
        article.setStatus(ArticleStatus.published);
        article.setPrimaryCategoryId(7L);
        article.setUpdatedAt(LocalDateTime.now());

        Category category = new Category();
        category.setId(7L);
        category.setSiteId(1L);
        category.setName("新闻");
        category.setFullPath("/news");
        category.setStatus("enabled");
        category.setPublicVisible(true);

        when(articleRepository.findById(11L)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndSiteId(7L, 1L)).thenReturn(Optional.of(category));
        when(searchIndexEntryRepository.findBySiteIdAndObjectTypeAndObjectId(1L, "content", 11L)).thenReturn(Optional.empty());
        when(searchIndexEntryRepository.save(any(SearchIndexEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        searchIndexService.syncContentEntry(11L);

        verify(searchIndexEntryRepository).save(any(SearchIndexEntry.class));
    }

    @Test
    void searchReturnsPagedResultsAndRecordsQuery() {
        PortalSearchItem item = new PortalSearchItem();
        item.setObjectType("content");
        item.setObjectId(11L);
        item.setTitle("新闻标题");
        item.setSummary("摘要");
        item.setPath("/news/11.html");
        item.setCategoryName("新闻");
        item.setPublishedAt(LocalDateTime.now());

        PortalSearchResponse expectedResponse = new PortalSearchResponse();
        expectedResponse.setItems(List.of(item));
        expectedResponse.setTotal(1);
        expectedResponse.setPage(0);
        expectedResponse.setSize(10);

        when(hibernateSearchService.search(1L, "新闻", 0, 10, "content", null, "publishedAt", "desc"))
                .thenReturn(expectedResponse);

        PortalSearchResponse response = searchIndexService.search(1L, "新闻", 0, 10, "content", null);

        assertEquals(1, response.getItems().size());
        assertEquals("content", response.getItems().get(0).getObjectType());
        verify(searchQueryLogService).record(1L, "新闻", "content", null, 1L);
    }

    @Test
    void listSuggestionsReturnsPopularSuggestions() {
        when(searchQueryLogService.listPopularSuggestions(1L, "政", 8, 7))
                .thenReturn(List.of(new SearchSuggestionItem("政务公开", "popular", 5L)));

        List<SearchSuggestionItem> response = searchIndexService.listSuggestions(1L, "政", 8, 7);

        assertEquals(1, response.size());
        assertEquals("政务公开", response.get(0).getKeyword());
    }

    @Test
    void getStatusReturnsAggregates() {
        AuditLog latestRebuild = new AuditLog();
        latestRebuild.setCreatedAt(LocalDateTime.now());
        latestRebuild.setSummary("已重建站点搜索索引");

        AuditLog latestFailure = new AuditLog();
        latestFailure.setFailureReason("索引更新失败");

        when(siteAccessService.resolveAccessibleSiteId(1L)).thenReturn(1L);
        when(searchIndexEntryRepository.countBySiteId(1L)).thenReturn(8L);
        when(auditLogRepository.findFirstBySiteIdAndObjectTypeOrderByCreatedAtDescIdDesc(1L, "search-index")).thenReturn(latestRebuild);
        when(auditLogRepository.findFirstBySiteIdAndObjectTypeAndResultOrderByCreatedAtDescIdDesc(1L, "search-index", "failed")).thenReturn(latestFailure);
        when(searchQueryLogService.listHotKeywords(1L, 10, 7)).thenReturn(List.of(new SearchKeywordStatItem("政务", 3)));
        when(searchQueryLogService.listZeroResultKeywords(1L, 10, 7)).thenReturn(List.of(new SearchKeywordStatItem("空词", 2)));
        when(searchQueryLogService.listLowResultKeywords(1L, 10, 7, 3)).thenReturn(List.of(new SearchKeywordStatItem("政策", 4)));

        SearchIndexStatusResponse response = searchIndexService.getStatus(1L, 10, 7);

        assertEquals(1L, response.getSiteId());
        assertEquals(8L, response.getTotalEntries());
        assertEquals("已重建站点搜索索引", response.getLastRebuildSummary());
        assertEquals("索引更新失败", response.getLastFailureReason());
        assertEquals(1, response.getHotKeywords().size());
        assertEquals(1, response.getZeroResultKeywords().size());
        assertEquals(1, response.getLowResultKeywords().size());
        assertNotNull(response.getLastRebuildAt());
    }

    @Test
    void rebuildSiteIndexForAdminWritesAudit() {
        when(siteAccessService.resolveAccessibleSiteId(1L)).thenReturn(1L);
        when(categoryRepository.findBySiteIdAndStatusOrderBySortOrderAscIdAsc(1L, "enabled")).thenReturn(List.of());
        when(articleRepository.findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(1L, ArticleStatus.published)).thenReturn(List.of());
        when(topicRepository.findBySiteIdAndStatusOrderByUpdatedAtDescIdDesc(1L, "active")).thenReturn(List.of());

        searchIndexService.rebuildSiteIndexForAdmin(1L);

        verify(auditLogService).record(eq("search_index_rebuild_site"), eq("search-index"), eq(1L), eq(1L), eq("success"), eq("已重建站点搜索索引"), eq(null), eq(null));
    }
}
