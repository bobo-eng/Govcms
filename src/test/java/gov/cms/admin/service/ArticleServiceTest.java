package gov.cms.admin.service;

import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.Category;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ArticleService articleService;

    @Test
    void createArticleSyncsCategoryNameFromPrimaryCategory() {
        Article article = new Article();
        article.setTitle("??????");
        article.setSiteId(1L);
        article.setPrimaryCategoryId(11L);
        article.setStatus("draft");

        Category category = new Category();
        category.setId(11L);
        category.setSiteId(1L);
        category.setName("????");

        when(categoryRepository.findById(11L)).thenReturn(Optional.of(category));
        doAnswer(invocation -> invocation.getArgument(0)).when(articleRepository).save(any(Article.class));

        Article saved = articleService.createArticle(article);

        assertEquals("????", saved.getCategory());
        assertEquals(1L, saved.getSiteId());
    }

    @Test
    void createArticleRejectsCategorySiteMismatch() {
        Article article = new Article();
        article.setTitle("??????");
        article.setSiteId(2L);
        article.setPrimaryCategoryId(11L);

        Category category = new Category();
        category.setId(11L);
        category.setSiteId(1L);
        category.setName("????");

        when(categoryRepository.findById(11L)).thenReturn(Optional.of(category));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> articleService.createArticle(article));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
