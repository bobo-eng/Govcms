package gov.cms.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateVersion;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.NavigationItemRepository;
import gov.cms.admin.repository.TopicRepository;
import gov.cms.admin.repository.TopicContentItemRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderContextAssemblerTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateVersionRepository templateVersionRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private NavigationItemRepository navigationItemRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private TopicContentItemRepository topicContentItemRepository;
    @Mock private ArticleRepository articleRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private RenderContextAssembler assembler;

    @Test
    void assemblePreviewIncludesWarningsForUnpublishedContent() {
        Template template = buildTemplate("content_detail", "active");
        TemplateVersion version = buildVersion(template.getId(), "{\"layout\":[{\"slot\":\"main\"}]}", "{\"blocks\":[{\"type\":\"content_header\",\"slot\":\"main\"},{\"type\":\"content_body\",\"slot\":\"main\"}]}");
        Article article = buildArticle(99L, ArticleStatus.draft);
        Category category = buildCategory(7L, "/news");

        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findById(101L)).thenReturn(Optional.of(version));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(buildSite()));
        when(articleRepository.findById(99L)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndSiteId(7L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(category));

        RenderRequest request = new RenderRequest();
        request.setSiteId(1L);
        request.setTemplateId(10L);
        request.setSourceType("content");
        request.setSourceId(99L);
        request.setMode("preview");

        RenderContextSnapshot snapshot = assembler.assemble(request);

        assertEquals("content-detail", snapshot.getPageType());
        assertFalse(snapshot.isPublishReady());
        assertTrue(snapshot.getWarnings().stream().anyMatch(item -> item.contains("not published")));
        assertEquals("/news/99.html", snapshot.getPathHint());
    }

    @Test
    void assemblePublishAllowsApprovedContent() {
        Template template = buildTemplate("content_detail", "active");
        TemplateVersion version = buildVersion(template.getId(), "{\"layout\":[{\"slot\":\"main\"}]}", "{\"blocks\":[{\"type\":\"content_header\",\"slot\":\"main\"}]}");
        Article article = buildArticle(99L, ArticleStatus.approved);
        Category category = buildCategory(7L, "/news");

        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findById(101L)).thenReturn(Optional.of(version));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(buildSite()));
        when(articleRepository.findById(99L)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndSiteId(7L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(category));

        RenderRequest request = new RenderRequest();
        request.setSiteId(1L);
        request.setTemplateId(10L);
        request.setSourceType("content");
        request.setSourceId(99L);
        request.setMode("publish");

        RenderContextSnapshot snapshot = assembler.assemble(request);

        assertTrue(snapshot.isPublishReady());
    }

    @Test
    void assembleColumnSnapshotBuildsArticleListBlockData() {
        Template template = buildTemplate("column_list", "active");
        TemplateVersion version = buildVersion(template.getId(), "{\"layout\":[{\"slot\":\"main\"}]}", "{\"blocks\":[{\"type\":\"article_list\",\"slot\":\"main\",\"props\":{\"size\":5}}]}");
        Category category = buildCategory(7L, "/news");
        Article article = buildArticle(88L, ArticleStatus.published);

        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findById(101L)).thenReturn(Optional.of(version));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(buildSite()));
        when(categoryRepository.findByIdAndSiteId(7L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(category));
        when(articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(eq(1L), eq(7L), eq(ArticleStatus.published), any())).thenReturn(List.of(article));

        RenderRequest request = new RenderRequest();
        request.setSiteId(1L);
        request.setTemplateId(10L);
        request.setSourceType("column");
        request.setSourceId(7L);
        request.setMode("preview");

        RenderContextSnapshot snapshot = assembler.assemble(request);

        assertEquals("column-list", snapshot.getPageType());
        assertTrue(String.valueOf(snapshot.getRenderBlocks().get(0).getData()).contains("/news/88.html"));
    }

    @Test
    void assemblePublishRejectsDraftContent() {
        Template template = buildTemplate("content_detail", "active");
        TemplateVersion version = buildVersion(template.getId(), "{\"layout\":[{\"slot\":\"main\"}]}", "{\"blocks\":[{\"type\":\"content_header\",\"slot\":\"main\"}]}");
        Article article = buildArticle(99L, ArticleStatus.draft);
        Category category = buildCategory(7L, "/news");

        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findById(101L)).thenReturn(Optional.of(version));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(buildSite()));
        when(articleRepository.findById(99L)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndSiteId(7L, 1L)).thenReturn(Optional.of(category));

        RenderRequest request = new RenderRequest();
        request.setSiteId(1L);
        request.setTemplateId(10L);
        request.setSourceType("content");
        request.setSourceId(99L);
        request.setMode("publish");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> assembler.assemble(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void assembleTopicPreviewBuildsTopicContext() {
        Template template = buildTemplate("topic_page", "active");
        TemplateVersion version = buildVersion(template.getId(), "{\"layout\":[{\"slot\":\"main\"}]}", "{\"blocks\":[]}");
        Topic topic = buildTopic(5L, "manual");
        Article article = buildArticle(88L, ArticleStatus.published);

        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findById(101L)).thenReturn(Optional.of(version));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(buildSite()));
        when(topicRepository.findByIdAndSiteId(5L, 1L)).thenReturn(Optional.of(topic));
        when(topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(5L)).thenReturn(List.of(buildTopicContentItem(5L, 88L, 0)));
        when(articleRepository.findAllById(List.of(88L))).thenReturn(List.of(article));
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(buildCategory(7L, "/news")));

        RenderRequest request = new RenderRequest();
        request.setSiteId(1L);
        request.setTemplateId(10L);
        request.setSourceType("topic");
        request.setSourceId(5L);
        request.setMode("preview");

        RenderContextSnapshot snapshot = assembler.assemble(request);

        assertEquals("topic-page", snapshot.getPageType());
        assertEquals("manual", String.valueOf(snapshot.getContext().get("topicContext") instanceof java.util.Map<?,?> map ? map.get("aggregationMode") : null));
        assertEquals("/topics/topic-1/index.html", snapshot.getPathHint());
        assertTrue(snapshot.isPublishReady());
    }

    private Template buildTemplate(String type, String status) {
        Template template = new Template();
        template.setId(10L);
        template.setSiteId(1L);
        template.setName("Template");
        template.setCode("template");
        template.setType(type);
        template.setStatus(status);
        template.setCurrentVersionId(101L);
        template.setDefaultPreviewSource("sample");
        return template;
    }

    private TemplateVersion buildVersion(Long templateId, String layoutSchema, String blockSchema) {
        TemplateVersion version = new TemplateVersion();
        version.setId(101L);
        version.setTemplateId(templateId);
        version.setVersionNo(2);
        version.setLayoutSchema(layoutSchema);
        version.setBlockSchema(blockSchema);
        version.setSeoSchema("{\"title\":\"demo\"}");
        version.setStyleSchema("{\"theme\":{}}");
        return version;
    }

    private Site buildSite() {
        Site site = new Site();
        site.setId(1L);
        site.setName("Gov Main");
        site.setCode("gov-main");
        site.setStatus("enabled");
        return site;
    }

    private Category buildCategory(Long id, String fullPath) {
        Category category = new Category();
        category.setId(id);
        category.setSiteId(1L);
        category.setName("News");
        category.setCode("news");
        category.setSlug("news");
        category.setFullPath(fullPath);
        category.setLevel(1);
        category.setStatus("enabled");
        category.setNavVisible(true);
        category.setBreadcrumbVisible(true);
        category.setPublicVisible(true);
        return category;
    }

    private Topic buildTopic(Long id, String aggregationMode) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setSiteId(1L);
        topic.setName("???");
        topic.setCode("topic-1");
        topic.setSlug("topic-1");
        topic.setStatus("active");
        topic.setTemplateId(10L);
        topic.setAggregationMode(aggregationMode);
        topic.setRuleLimit(10);
        return topic;
    }

    private gov.cms.admin.entity.TopicContentItem buildTopicContentItem(Long topicId, Long articleId, int sortOrder) {
        gov.cms.admin.entity.TopicContentItem item = new gov.cms.admin.entity.TopicContentItem();
        item.setTopicId(topicId);
        item.setArticleId(articleId);
        item.setSortOrder(sortOrder);
        return item;
    }

    private Article buildArticle(Long id, ArticleStatus status) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(1L);
        article.setPrimaryCategoryId(7L);
        article.setTitle("Article");
        article.setSummary("Summary");
        article.setContent("<p>Body</p>");
        article.setAuthor("editor");
        article.setStatus(status);
        article.setViews(12);
        article.setCreatedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        article.setUpdatedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        return article;
    }
}
