package gov.cms.admin.service;

import gov.cms.admin.dto.ArticleRejectRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleLifecycleHistory;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Template;
import gov.cms.admin.repository.ArticleLifecycleHistoryRepository;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private TemplateBindingRepository templateBindingRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private ArticleLifecycleHistoryRepository articleLifecycleHistoryRepository;

    @InjectMocks
    private ArticleService articleService;

    @Test
    void createArticleSyncsCategoryNameFromPrimaryCategory() {
        Article article = new Article();
        article.setTitle("Test article");
        article.setSiteId(1L);
        article.setPrimaryCategoryId(11L);
        article.setStatus(ArticleStatus.draft);

        Category category = new Category();
        category.setId(11L);
        category.setSiteId(1L);
        category.setName("News");

        when(categoryRepository.findById(11L)).thenReturn(Optional.of(category));
        doAnswer(invocation -> invocation.getArgument(0)).when(articleRepository).save(any(Article.class));

        Article saved = articleService.createArticle(article);

        assertEquals("News", saved.getCategory());
        assertEquals(ArticleStatus.draft, saved.getStatus());
    }

    @Test
    void submitReviewTransitionsDraftAndWritesHistory() {
        Article article = new Article();
        article.setId(9L);
        article.setSiteId(1L);
        article.setPrimaryCategoryId(11L);
        article.setTitle("Test article");
        article.setContent("<p>Body</p>");
        article.setStatus(ArticleStatus.draft);

        Category category = new Category();
        category.setId(11L);
        category.setSiteId(1L);
        category.setName("News");
        category.setStatus("enabled");
        category.setPublicVisible(true);
        category.setDetailTemplateId(101L);

        Site site = new Site();
        site.setId(1L);
        site.setStatus("enabled");

        Template template = new Template();
        template.setId(101L);
        template.setName("Detail");
        template.setStatus("active");

        when(articleRepository.findById(9L)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndSiteId(11L, 1L)).thenReturn(Optional.of(category));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(templateRepository.findById(101L)).thenReturn(Optional.of(template));
        doAnswer(invocation -> invocation.getArgument(0)).when(articleRepository).save(any(Article.class));

        Article saved = articleService.submitReview(9L);

        assertEquals(ArticleStatus.pending_review, saved.getStatus());
        verify(articleLifecycleHistoryRepository).save(any(ArticleLifecycleHistory.class));
    }

    @Test
    void approveMovesPendingReviewToApproved() {
        Article article = new Article();
        article.setId(9L);
        article.setSiteId(1L);
        article.setPrimaryCategoryId(11L);
        article.setTitle("Test article");
        article.setContent("<p>Body</p>");
        article.setStatus(ArticleStatus.pending_review);

        Category category = new Category();
        category.setId(11L);
        category.setSiteId(1L);
        category.setName("News");
        category.setStatus("enabled");
        category.setPublicVisible(true);
        category.setDetailTemplateId(101L);

        Site site = new Site();
        site.setId(1L);
        site.setStatus("enabled");

        Template template = new Template();
        template.setId(101L);
        template.setName("Detail");
        template.setStatus("active");

        when(articleRepository.findById(9L)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndSiteId(11L, 1L)).thenReturn(Optional.of(category));
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(templateRepository.findById(101L)).thenReturn(Optional.of(template));
        doAnswer(invocation -> invocation.getArgument(0)).when(articleRepository).save(any(Article.class));

        Article saved = articleService.approve(9L);

        assertEquals(ArticleStatus.approved, saved.getStatus());
    }

    @Test
    void rejectRequiresReason() {
        Article article = new Article();
        article.setId(9L);
        article.setStatus(ArticleStatus.pending_review);
        when(articleRepository.findById(9L)).thenReturn(Optional.of(article));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> articleService.reject(9L, new ArticleRejectRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}