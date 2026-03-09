package gov.cms.admin.service;

import gov.cms.admin.entity.Article;
import gov.cms.admin.repository.ArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public Page<Article> searchArticles(String keyword, String category, String status, Pageable pageable) {
        return articleRepository.searchArticles(keyword, category, status, pageable);
    }

    public Article getArticleById(Long id) {
        return articleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在"));
    }

    @Transactional
    public Article createArticle(Article article) {
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
}
