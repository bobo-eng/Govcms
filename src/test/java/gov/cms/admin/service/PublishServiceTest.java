package gov.cms.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.ArticlePublishCheckResponse;
import gov.cms.admin.dto.PublishCheckResponse;
import gov.cms.admin.dto.PublishRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.Site;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.NavigationItemRepository;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishImpactItemRepository;
import gov.cms.admin.repository.PublishJobRepository;
import gov.cms.admin.repository.PublishRollbackRecordRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TopicRepository;
import gov.cms.admin.repository.TopicContentItemRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishServiceTest {

    @Mock private PublishJobRepository publishJobRepository;
    @Mock private PublishImpactItemRepository publishImpactItemRepository;
    @Mock private PublishArtifactRepository publishArtifactRepository;
    @Mock private PublishRollbackRecordRepository publishRollbackRecordRepository;
    @Mock private PublishImpactCalculator publishImpactCalculator;
    @Mock private ArticleRepository articleRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private NavigationItemRepository navigationItemRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private TopicContentItemRepository topicContentItemRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateBindingRepository templateBindingRepository;
    @Mock private ArticleService articleService;
    @Mock private RenderContextAssembler renderContextAssembler;
    @Mock private MediaReferenceService mediaReferenceService;
    @Mock private PortalRenderService portalRenderService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AuditLogService auditLogService;
    @Mock private SiteAccessService siteAccessService;
    @Mock private Scheduler scheduler;
    @Mock private PublishExecutor publishExecutor;

    @InjectMocks
    private PublishService publishService;

    @Test
    void checkReturnsNotPublishableWhenArticleIsNotApproved() {
        PublishRequest request = new PublishRequest();
        request.setSiteId(1L);
        request.setUnitType("content");
        request.setUnitIds(List.of(9L));
        request.setMode("incremental");

        Site site = new Site();
        site.setId(1L);
        site.setStatus("enabled");

        Article article = new Article();
        article.setId(9L);
        article.setSiteId(1L);
        article.setStatus(ArticleStatus.draft);

        ArticlePublishCheckResponse articleCheck = new ArticlePublishCheckResponse();
        articleCheck.setArticleId(9L);
        articleCheck.setPublishable(false);
        articleCheck.setReasons(List.of("content status must be approved before formal publishing"));

        PublishImpactCalculator.ImpactPlan plan = new PublishImpactCalculator.ImpactPlan();
        plan.addItem(new PublishImpactItem());

        when(publishImpactCalculator.normalizeUnitType("content")).thenReturn("content");
        when(publishImpactCalculator.normalizeMode("incremental", "content")).thenReturn("incremental");
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(articleRepository.findById(9L)).thenReturn(Optional.of(article));
        when(articleService.publishCheck(9L)).thenReturn(articleCheck);
        when(publishImpactCalculator.calculate(any())).thenReturn(plan);

        PublishCheckResponse response = publishService.check(request);

        assertFalse(response.isPublishable());
        assertEquals(1, response.getReasons().size());
    }

    @Test
    void createAndExecuteRejectsConflictWhenPrecheckFails() {
        PublishRequest request = new PublishRequest();
        request.setSiteId(1L);
        request.setUnitType("content");
        request.setUnitIds(List.of(9L));
        request.setMode("incremental");

        Site site = new Site();
        site.setId(1L);
        site.setStatus("enabled");

        Article article = new Article();
        article.setId(9L);
        article.setSiteId(1L);
        article.setStatus(ArticleStatus.draft);

        ArticlePublishCheckResponse articleCheck = new ArticlePublishCheckResponse();
        articleCheck.setArticleId(9L);
        articleCheck.setPublishable(false);
        articleCheck.setReasons(List.of("content status must be approved before formal publishing"));

        PublishImpactCalculator.ImpactPlan plan = new PublishImpactCalculator.ImpactPlan();
        plan.addItem(new PublishImpactItem());

        when(publishImpactCalculator.normalizeUnitType("content")).thenReturn("content");
        when(publishImpactCalculator.normalizeMode("incremental", "content")).thenReturn("incremental");
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(articleRepository.findById(9L)).thenReturn(Optional.of(article));
        when(articleService.publishCheck(9L)).thenReturn(articleCheck);
        when(publishImpactCalculator.calculate(any())).thenReturn(plan);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> publishService.createAndQueue(request, "production", null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void checkReturnsPublishableWhenTopicIsReady() {
        PublishRequest request = new PublishRequest();
        request.setSiteId(1L);
        request.setUnitType("topic");
        request.setUnitIds(List.of(5L));
        request.setMode("incremental");

        Site site = new Site();
        site.setId(1L);
        site.setStatus("enabled");

        var topic = new gov.cms.admin.entity.Topic();
        topic.setId(5L);
        topic.setSiteId(1L);
        topic.setStatus("active");
        topic.setTemplateId(99L);
        topic.setAggregationMode("manual");

        var template = new gov.cms.admin.entity.Template();
        template.setId(99L);
        template.setSiteId(1L);
        template.setStatus("active");
        template.setType("topic_page");

        var item = new gov.cms.admin.entity.TopicContentItem();
        item.setTopicId(5L);
        item.setArticleId(11L);
        var article = new Article();
        article.setId(11L);
        article.setSiteId(1L);
        article.setStatus(ArticleStatus.published);

        PublishImpactCalculator.ImpactPlan plan = new PublishImpactCalculator.ImpactPlan();
        plan.addItem(new PublishImpactItem());

        when(publishImpactCalculator.normalizeUnitType("topic")).thenReturn("topic");
        when(publishImpactCalculator.normalizeMode("incremental", "topic")).thenReturn("incremental");
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(topicRepository.findByIdAndSiteId(5L, 1L)).thenReturn(Optional.of(topic));
        when(templateRepository.findByIdAndSiteId(99L, 1L)).thenReturn(Optional.of(template));
        when(topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(5L)).thenReturn(List.of(item));
        when(articleRepository.findAllById(List.of(11L))).thenReturn(List.of(article));
        when(publishImpactCalculator.calculate(any())).thenReturn(plan);

        PublishCheckResponse response = publishService.check(request);

        assertEquals(true, response.isPublishable());
    }

    @Test
    void warningFromMissingMediaForTopicIsReturnedInCheck() {
        PublishRequest request = new PublishRequest();
        request.setSiteId(1L);
        request.setUnitType("topic");
        request.setUnitIds(List.of(5L));
        request.setMode("incremental");

        Site site = new Site();
        site.setId(1L);
        site.setStatus("enabled");

        var topic = new gov.cms.admin.entity.Topic();
        topic.setId(5L);
        topic.setSiteId(1L);
        topic.setStatus("active");
        topic.setTemplateId(99L);
        topic.setAggregationMode("manual");

        var template = new gov.cms.admin.entity.Template();
        template.setId(99L);
        template.setSiteId(1L);
        template.setStatus("active");
        template.setType("topic_page");

        var item = new gov.cms.admin.entity.TopicContentItem();
        item.setTopicId(5L);
        item.setArticleId(11L);
        var article = new Article();
        article.setId(11L);
        article.setSiteId(1L);
        article.setStatus(ArticleStatus.published);

        PublishImpactCalculator.ImpactPlan plan = new PublishImpactCalculator.ImpactPlan();
        plan.addItem(new PublishImpactItem());

        when(publishImpactCalculator.normalizeUnitType("topic")).thenReturn("topic");
        when(publishImpactCalculator.normalizeMode("incremental", "topic")).thenReturn("incremental");
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(topicRepository.findByIdAndSiteId(5L, 1L)).thenReturn(Optional.of(topic));
        when(templateRepository.findByIdAndSiteId(99L, 1L)).thenReturn(Optional.of(template));
        when(topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(5L)).thenReturn(List.of(item));
        when(articleRepository.findAllById(List.of(11L))).thenReturn(List.of(article));
        when(publishImpactCalculator.calculate(any())).thenReturn(plan);
        when(mediaReferenceService.collectMissingMediaWarningsForTopic(topic)).thenReturn(List.of("专题 #5 引用了不存在的媒体 #3"));

        PublishCheckResponse response = publishService.check(request);

        assertEquals(1, response.getWarnings().size());
    }
}
