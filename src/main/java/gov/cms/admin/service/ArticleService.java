package gov.cms.admin.service;

import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.Category;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;

    public ArticleService(ArticleRepository articleRepository, CategoryRepository categoryRepository) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
    }

    public Page<Article> searchArticles(String keyword, String category, String status, Long siteId, Pageable pageable) {
        return articleRepository.searchArticles(keyword, category, status, siteId, pageable);
    }

    public Article getArticleById(Long id) {
        return articleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在"));
    }

    @Transactional
    public Article createArticle(Article article) {
        prepareForSave(article);
        if (article.getStatus() == null) {
            article.setStatus("draft");
        }
        if (article.getViews() == null) {
            article.setViews(0);
        }
        return articleRepository.save(article);
    }

    @Transactional
    public Article updateArticle(Long id, Article articleData) {
        Article article = getArticleById(id);
        article.setTitle(articleData.getTitle());
        article.setContent(articleData.getContent());
        article.setSummary(articleData.getSummary());
        article.setCategory(articleData.getCategory());
        article.setAuthor(articleData.getAuthor());
        article.setStatus(articleData.getStatus());
        article.setSiteId(articleData.getSiteId());
        article.setPrimaryCategoryId(articleData.getPrimaryCategoryId());
        prepareForSave(article);
        return articleRepository.save(article);
    }

    @Transactional
    public void deleteArticle(Long id) {
        Article article = getArticleById(id);
        articleRepository.delete(article);
    }

    @Transactional
    public Article publishArticle(Long id) {
        Article article = getArticleById(id);
        article.setStatus("published");
        return articleRepository.save(article);
    }

    @Transactional
    public Article unpublishArticle(Long id) {
        Article article = getArticleById(id);
        article.setStatus("draft");
        return articleRepository.save(article);
    }

    private void prepareForSave(Article article) {
        if (article == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文章数据不能为空");
        }
        if (article.getTitle() == null || article.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文章标题不能为空");
        }
        article.setTitle(article.getTitle().trim());
        if (article.getAuthor() != null) {
            article.setAuthor(article.getAuthor().trim());
        }
        if (article.getSummary() != null) {
            article.setSummary(article.getSummary().trim());
        }
        if (article.getContent() != null) {
            article.setContent(article.getContent().trim());
        }
        if (article.getStatus() == null || article.getStatus().isBlank()) {
            article.setStatus("draft");
        }

        syncCategoryName(article);
    }

    private void syncCategoryName(Article article) {
        if (article.getPrimaryCategoryId() == null) {
            if (article.getCategory() != null) {
                article.setCategory(article.getCategory().trim());
            }
            return;
        }

        Category category = categoryRepository.findById(article.getPrimaryCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选栏目不存在"));

        if (article.getSiteId() == null) {
            article.setSiteId(category.getSiteId());
        }
        if (!category.getSiteId().equals(article.getSiteId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文章所属站点与栏目不一致");
        }
        article.setCategory(category.getName());
    }
}
