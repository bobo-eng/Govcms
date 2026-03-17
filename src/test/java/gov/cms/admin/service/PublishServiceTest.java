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
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishImpactItemRepository;
import gov.cms.admin.repository.PublishJobRepository;
import gov.cms.admin.repository.PublishRollbackRecordRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateBindingRepository templateBindingRepository;
    @Mock private ArticleService articleService;
    @Mock private RenderContextAssembler renderContextAssembler;
    @Mock private PortalRenderService portalRenderService;
    @Mock private ObjectMapper objectMapper;

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

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> publishService.createAndExecute(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}