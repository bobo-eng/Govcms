package gov.cms.admin.service;

import gov.cms.admin.dto.PortalSearchCategoryItem;
import gov.cms.admin.dto.PortalSearchItem;
import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.dto.SearchIndexStatusResponse;
import gov.cms.admin.dto.SearchSuggestionItem;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.SearchIndexEntry;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.AuditLogRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SearchIndexEntryRepository;
import gov.cms.admin.repository.TopicRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class SearchIndexService {

    private final SearchIndexEntryRepository searchIndexEntryRepository;
    private final ArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final SearchQueryLogService searchQueryLogService;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final SiteAccessService siteAccessService;
    private final HibernateSearchService hibernateSearchService;
    private final SuggestionIndexerService suggestionIndexerService;

    public SearchIndexService(SearchIndexEntryRepository searchIndexEntryRepository,
                              ArticleRepository articleRepository,
                              TopicRepository topicRepository,
                              CategoryRepository categoryRepository,
                              SearchQueryLogService searchQueryLogService,
                              AuditLogService auditLogService,
                              AuditLogRepository auditLogRepository,
                              SiteAccessService siteAccessService,
                              HibernateSearchService hibernateSearchService,
                              SuggestionIndexerService suggestionIndexerService) {
        this.searchIndexEntryRepository = searchIndexEntryRepository;
        this.articleRepository = articleRepository;
        this.topicRepository = topicRepository;
        this.categoryRepository = categoryRepository;
        this.searchQueryLogService = searchQueryLogService;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
        this.siteAccessService = siteAccessService;
        this.hibernateSearchService = hibernateSearchService;
        this.suggestionIndexerService = suggestionIndexerService;
    }

    @Transactional
    public void rebuildSiteIndex(Long siteId) {
        searchIndexEntryRepository.deleteAll(searchIndexEntryRepository.findBySiteId(siteId));
        suggestionIndexerService.clearSiteIndex(siteId);
        for (Category category : categoryRepository.findBySiteIdAndStatusOrderBySortOrderAscIdAsc(siteId, "enabled")) {
            syncCategoryEntry(category.getId());
        }
        for (Article article : articleRepository.findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(siteId, ArticleStatus.published)) {
            syncContentEntry(article.getId());
        }
        for (Topic topic : topicRepository.findBySiteIdAndStatusOrderByUpdatedAtDescIdDesc(siteId, "active")) {
            syncTopicEntry(topic.getId());
        }
    }

    @Transactional
    public void syncContentEntry(Long articleId) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null || article.getStatus() != ArticleStatus.published || article.getSiteId() == null) {
            if (article != null && article.getSiteId() != null) {
                searchIndexEntryRepository.deleteBySiteIdAndObjectTypeAndObjectId(article.getSiteId(), "content", articleId);
            }
            return;
        }
        Category category = article.getPrimaryCategoryId() == null
                ? null
                : categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), article.getSiteId()).orElse(null);
        if (category == null || !Boolean.TRUE.equals(category.getPublicVisible()) || !"enabled".equalsIgnoreCase(category.getStatus())) {
            searchIndexEntryRepository.deleteBySiteIdAndObjectTypeAndObjectId(article.getSiteId(), "content", articleId);
            return;
        }
        SearchIndexEntry entry = searchIndexEntryRepository
                .findBySiteIdAndObjectTypeAndObjectId(article.getSiteId(), "content", articleId)
                .orElseGet(SearchIndexEntry::new);
        entry.setSiteId(article.getSiteId());
        entry.setObjectType("content");
        entry.setObjectId(article.getId());
        entry.setTitle(article.getTitle());
        entry.setSummary(article.getSummary());
        entry.setKeywords(article.getCategory());
        entry.setPath(category.getFullPath() + "/" + article.getId() + ".html");
        entry.setStatus("published");
        entry.setPublishedAt(Optional.ofNullable(article.getPublishedAt()).orElse(article.getUpdatedAt()));
        entry.setCategoryId(category.getId());
        entry.setCategoryName(category.getName());
        entry.setTopicName(null);
        entry.setSearchText(String.join(" ",
                Optional.ofNullable(article.getTitle()).orElse(""),
                Optional.ofNullable(article.getSummary()).orElse(""),
                Optional.ofNullable(article.getContent()).orElse(""),
                Optional.ofNullable(category.getName()).orElse("")));
        searchIndexEntryRepository.save(entry);
        suggestionIndexerService.indexTitle(entry.getSiteId(), entry.getTitle());
    }

    @Transactional
    public void syncTopicEntry(Long topicId) {
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic == null || topic.getSiteId() == null || !"active".equalsIgnoreCase(topic.getStatus()) || topic.getSlug() == null || topic.getSlug().isBlank()) {
            if (topic != null && topic.getSiteId() != null) {
                searchIndexEntryRepository.deleteBySiteIdAndObjectTypeAndObjectId(topic.getSiteId(), "topic", topicId);
            }
            return;
        }
        SearchIndexEntry entry = searchIndexEntryRepository
                .findBySiteIdAndObjectTypeAndObjectId(topic.getSiteId(), "topic", topicId)
                .orElseGet(SearchIndexEntry::new);
        entry.setSiteId(topic.getSiteId());
        entry.setObjectType("topic");
        entry.setObjectId(topic.getId());
        entry.setTitle(topic.getName());
        entry.setSummary(topic.getSummary());
        entry.setKeywords(topic.getSeoKeywords());
        entry.setPath("/topics/" + topic.getSlug() + "/index.html");
        entry.setStatus("published");
        entry.setPublishedAt(topic.getUpdatedAt());
        entry.setCategoryId(topic.getRuleCategoryId());
        entry.setCategoryName(topic.getRuleCategoryId() == null ? null : categoryRepository.findByIdAndSiteId(topic.getRuleCategoryId(), topic.getSiteId()).map(Category::getName).orElse(null));
        entry.setTopicName(topic.getName());
        entry.setSearchText(String.join(" ",
                Optional.ofNullable(topic.getName()).orElse(""),
                Optional.ofNullable(topic.getSummary()).orElse(""),
                Optional.ofNullable(topic.getSeoTitle()).orElse(""),
                Optional.ofNullable(topic.getSeoKeywords()).orElse(""),
                Optional.ofNullable(topic.getSeoDescription()).orElse("")));
        searchIndexEntryRepository.save(entry);
        suggestionIndexerService.indexTitle(entry.getSiteId(), entry.getTitle());
    }

    @Transactional
    public void syncCategoryEntry(Long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null || category.getSiteId() == null || !"enabled".equalsIgnoreCase(category.getStatus()) || !Boolean.TRUE.equals(category.getPublicVisible())) {
            if (category != null && category.getSiteId() != null) {
                searchIndexEntryRepository.deleteBySiteIdAndObjectTypeAndObjectId(category.getSiteId(), "category", categoryId);
            }
            return;
        }
        SearchIndexEntry entry = searchIndexEntryRepository
                .findBySiteIdAndObjectTypeAndObjectId(category.getSiteId(), "category", categoryId)
                .orElseGet(SearchIndexEntry::new);
        entry.setSiteId(category.getSiteId());
        entry.setObjectType("category");
        entry.setObjectId(category.getId());
        entry.setTitle(category.getName());
        entry.setSummary(category.getDescription());
        entry.setKeywords(category.getSeoKeywords());
        entry.setPath(category.getFullPath() + "/index.html");
        entry.setStatus("published");
        entry.setPublishedAt(category.getUpdatedAt());
        entry.setCategoryId(category.getId());
        entry.setCategoryName(category.getName());
        entry.setTopicName(null);
        entry.setSearchText(String.join(" ",
                Optional.ofNullable(category.getName()).orElse(""),
                Optional.ofNullable(category.getDescription()).orElse(""),
                Optional.ofNullable(category.getSeoTitle()).orElse(""),
                Optional.ofNullable(category.getSeoKeywords()).orElse(""),
                Optional.ofNullable(category.getSeoDescription()).orElse("")));
        searchIndexEntryRepository.save(entry);
        suggestionIndexerService.indexTitle(entry.getSiteId(), entry.getTitle());
    }

    @Transactional
    public void removeContentEntry(Long siteId, Long articleId) {
        searchIndexEntryRepository.deleteBySiteIdAndObjectTypeAndObjectId(siteId, "content", articleId);
    }

    public PortalSearchResponse search(Long siteId, String keyword, int page, int size, String type, Long categoryId) {
        return search(siteId, keyword, page, size, type, categoryId, "publishedAt", "desc");
    }

    public PortalSearchResponse search(Long siteId, String keyword, int page, int size,
                                       String type, Long categoryId, String sortField, String sortDirection) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        PortalSearchResponse response = hibernateSearchService.search(siteId, safeKeyword, safePage, safeSize, type, categoryId, sortField, sortDirection);
        searchQueryLogService.record(siteId, safeKeyword, type, categoryId, response.getTotal());
        return response;
    }

    public void rebuildSearchIndex() {
        hibernateSearchService.rebuildIndex();
    }

    public List<SearchSuggestionItem> listSuggestions(Long siteId, String keyword, int limit, int days) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        return searchQueryLogService.listPopularSuggestions(siteId, keyword, limit, days);
    }

    public List<PortalSearchCategoryItem> listCategories(Long siteId) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        return categoryRepository.findBySiteIdAndStatusOrderBySortOrderAscIdAsc(siteId, "enabled").stream()
                .filter(category -> Boolean.TRUE.equals(category.getPublicVisible()))
                .map(category -> new PortalSearchCategoryItem(category.getId(), category.getName()))
                .toList();
    }

    public SearchIndexStatusResponse getStatus(Long siteId, int limit, int days) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(siteId);
        SearchIndexStatusResponse response = new SearchIndexStatusResponse();
        response.setSiteId(accessibleSiteId);
        response.setTotalEntries(searchIndexEntryRepository.countBySiteId(accessibleSiteId));
        AuditLog latestRebuild = auditLogRepository.findFirstBySiteIdAndObjectTypeOrderByCreatedAtDescIdDesc(accessibleSiteId, "search-index");
        AuditLog latestFailure = auditLogRepository.findFirstBySiteIdAndObjectTypeAndResultOrderByCreatedAtDescIdDesc(accessibleSiteId, "search-index", "failed");
        if (latestRebuild != null) {
            response.setLastRebuildAt(latestRebuild.getCreatedAt());
            response.setLastRebuildSummary(latestRebuild.getSummary());
        }
        if (latestFailure != null) {
            response.setLastFailureReason(latestFailure.getFailureReason());
        }
        response.setHotKeywords(searchQueryLogService.listHotKeywords(accessibleSiteId, limit, days));
        response.setZeroResultKeywords(searchQueryLogService.listZeroResultKeywords(accessibleSiteId, limit, days));
        response.setLowResultKeywords(searchQueryLogService.listLowResultKeywords(accessibleSiteId, limit, days, 3));
        return response;
    }

    @Transactional
    public void rebuildSiteIndexForAdmin(Long siteId) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(siteId);
        executeRebuildAudit(accessibleSiteId, accessibleSiteId, "search_index_rebuild_site", "已重建站点搜索索引", () -> rebuildSiteIndex(accessibleSiteId));
    }

    @Transactional
    public Long rebuildContentIndexForAdmin(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found."));
        if (article.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content site is missing.");
        }
        siteAccessService.assertAccessibleSite(article.getSiteId());
        executeRebuildAudit(article.getSiteId(), articleId, "search_index_rebuild_content", "已重建内容搜索索引", () -> syncContentEntry(articleId));
        return article.getSiteId();
    }

    @Transactional
    public Long rebuildTopicIndexForAdmin(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found."));
        if (topic.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic site is missing.");
        }
        siteAccessService.assertAccessibleSite(topic.getSiteId());
        executeRebuildAudit(topic.getSiteId(), topicId, "search_index_rebuild_topic", "已重建专题搜索索引", () -> syncTopicEntry(topicId));
        return topic.getSiteId();
    }

    @Transactional
    public Long rebuildCategoryIndexForAdmin(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found."));
        if (category.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category site is missing.");
        }
        siteAccessService.assertAccessibleSite(category.getSiteId());
        executeRebuildAudit(category.getSiteId(), categoryId, "search_index_rebuild_category", "已重建栏目搜索索引", () -> syncCategoryEntry(categoryId));
        return category.getSiteId();
    }

    private void executeRebuildAudit(Long siteId, Long objectId, String actionType, String summary, Runnable action) {
        try {
            action.run();
            auditLogService.record(actionType, "search-index", objectId, siteId, "success", summary, null, null);
        } catch (RuntimeException exception) {
            auditLogService.record(actionType, "search-index", objectId, siteId, "failed", summary, exception.getMessage(), null);
            throw exception;
        }
    }

    private PortalSearchItem toItem(SearchIndexEntry entry) {
        PortalSearchItem item = new PortalSearchItem();
        item.setObjectType(entry.getObjectType());
        item.setObjectId(entry.getObjectId());
        item.setTitle(entry.getTitle());
        item.setSummary(entry.getSummary());
        item.setPath(entry.getPath());
        item.setCategoryName(entry.getCategoryName());
        item.setPublishedAt(entry.getPublishedAt());
        return item;
    }
}

