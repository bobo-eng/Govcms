package gov.cms.admin.controller;

import gov.cms.admin.dto.ArticleOfflineRequest;
import gov.cms.admin.dto.ArticlePublishCheckResponse;
import gov.cms.admin.dto.ArticleRejectRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleLifecycleHistory;
import gov.cms.admin.service.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@CrossOrigin(origins = "*")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('content:article:view')")
    public ResponseEntity<Page<Article>> getArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long primaryCategoryId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(articleService.searchArticles(keyword, category, status, siteId, primaryCategoryId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('content:article:view')")
    public ResponseEntity<Article> getArticle(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.getArticleById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:article:create')")
    public ResponseEntity<Article> createArticle(@RequestBody Article article) {
        return ResponseEntity.status(HttpStatus.CREATED).body(articleService.createArticle(article));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('content:article:update')")
    public ResponseEntity<Article> updateArticle(@PathVariable Long id, @RequestBody Article article) {
        return ResponseEntity.ok(articleService.updateArticle(id, article));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:article:delete')")
    public ResponseEntity<Void> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize("hasAuthority('content:article:submit-review')")
    public ResponseEntity<Article> submitReview(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.submitReview(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('content:article:review')")
    public ResponseEntity<Article> approve(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('content:article:reject')")
    public ResponseEntity<Article> reject(@PathVariable Long id, @RequestBody ArticleRejectRequest request) {
        return ResponseEntity.ok(articleService.reject(id, request));
    }

    @GetMapping("/{id}/publish-check")
    @PreAuthorize("hasAuthority('content:article:view')")
    public ResponseEntity<ArticlePublishCheckResponse> publishCheck(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.publishCheck(id));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('content:article:offline')")
    public ResponseEntity<Article> offline(@PathVariable Long id, @RequestBody ArticleOfflineRequest request) {
        return ResponseEntity.ok(articleService.offline(id, request));
    }

    @GetMapping("/{id}/histories")
    @PreAuthorize("hasAuthority('content:article:history:view')")
    public ResponseEntity<List<ArticleLifecycleHistory>> getHistories(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.listHistories(id));
    }
}