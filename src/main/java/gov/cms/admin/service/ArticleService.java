package gov.cms.admin.service;

import gov.cms.admin.dto.ArticleOfflineRequest;
import gov.cms.admin.dto.ArticlePublishCheckResponse;
import gov.cms.admin.dto.ArticleRejectRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleLifecycleAction;
import gov.cms.admin.entity.ArticleLifecycleHistory;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.repository.ArticleLifecycleHistoryRepository;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final SiteRepository siteRepository;
    private final TemplateBindingRepository templateBindingRepository;
    private final TemplateRepository templateRepository;
    private final ArticleLifecycleHistoryRepository articleLifecycleHistoryRepository;

    public ArticleService(ArticleRepository articleRepository,
                          CategoryRepository categoryRepository,
                          SiteRepository siteRepository,
                          TemplateBindingRepository templateBindingRepository,
                          TemplateRepository templateRepository,
                          ArticleLifecycleHistoryRepository articleLifecycleHistoryRepository) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
        this.templateBindingRepository = templateBindingRepository;
        this.templateRepository = templateRepository;
        this.articleLifecycleHistoryRepository = articleLifecycleHistoryRepository;
    }

    public Page<Article> searchArticles(String keyword,
                                        String category,
                                        String status,
                                        Long siteId,
                                        Long primaryCategoryId,
                                        Pageable pageable) {
        return articleRepository.searchArticles(keyword, category, ArticleStatus.fromNullable(status), siteId, primaryCategoryId, pageable);
    }

    public Article getArticleById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found."));
    }

    public List<ArticleLifecycleHistory> listHistories(Long articleId) {
        Article article = getArticleById(articleId);
        return articleLifecycleHistoryRepository.findByArticleIdOrderByCreatedAtDescIdDesc(article.getId());
    }

    @Transactional
    public Article createArticle(Article article) {
        prepareEditablePayload(article);
        if (article.getCurrentRevision() == null || article.getCurrentRevision() < 1) {
            article.setCurrentRevision(1);
        }
        article.setStatus(ArticleStatus.draft);
        article.setRejectionReason(null);
        article.setOfflineReason(null);
        return articleRepository.save(article);
    }

    @Transactional
    public Article updateArticle(Long id, Article articleData) {
        Article existingArticle = getArticleById(id);
        ensureEditable(existingArticle);
        prepareEditablePayload(articleData);
        existingArticle.setTitle(articleData.getTitle());
        existingArticle.setContent(articleData.getContent());
        existingArticle.setSummary(articleData.getSummary());
        existingArticle.setAuthor(articleData.getAuthor());
        existingArticle.setSiteId(articleData.getSiteId());
        existingArticle.setPrimaryCategoryId(articleData.getPrimaryCategoryId());
        syncCategoryName(existingArticle);
        existingArticle.setCurrentRevision(Optional.ofNullable(existingArticle.getCurrentRevision()).orElse(1) + 1);
        return articleRepository.save(existingArticle);
    }

    @Transactional
    public void deleteArticle(Long id) {
        Article article = getArticleById(id);
        ensureEditable(article);
        articleRepository.delete(article);
    }

    @Transactional
    public Article submitReview(Long id) {
        Article article = getArticleById(id);
        if (!(article.getStatus() == ArticleStatus.draft || article.getStatus() == ArticleStatus.rejected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or rejected content can be submitted for review.");
        }
        validateBeforeSubmitReview(article);
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.pending_review);
        article.setSubmittedAt(LocalDateTime.now());
        article.setSubmittedBy(currentOperatorName());
        article.setRejectionReason(null);
        article.setApprovedAt(null);
        article.setApprovedBy(null);
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.submit_review, fromStatus, saved.getStatus(), currentOperatorName(), null, null);
        return saved;
    }

    @Transactional
    public Article approve(Long id) {
        Article article = getArticleById(id);
        if (article.getStatus() != ArticleStatus.pending_review) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending review content can be approved.");
        }
        validateBeforeSubmitReview(article);
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.approved);
        article.setApprovedAt(LocalDateTime.now());
        article.setApprovedBy(currentOperatorName());
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.approve, fromStatus, saved.getStatus(), currentOperatorName(), null, null);
        return saved;
    }

    @Transactional
    public Article reject(Long id, ArticleRejectRequest request) {
        Article article = getArticleById(id);
        if (article.getStatus() != ArticleStatus.pending_review) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending review content can be rejected.");
        }
        String reason = normalizeReason(request == null ? null : request.getReason(), "Reject reason is required.");
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.rejected);
        article.setRejectionReason(reason);
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.reject, fromStatus, saved.getStatus(), currentOperatorName(), reason, null);
        return saved;
    }

    public ArticlePublishCheckResponse publishCheck(Long id) {
        Article article = getArticleById(id);
        return buildPublishCheck(article);
    }

    @Transactional
    public Article offline(Long id, ArticleOfflineRequest request) {
        Article article = getArticleById(id);
        if (article.getStatus() != ArticleStatus.published) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only published content can be offlined.");
        }
        String reason = normalizeReason(request == null ? null : request.getReason(), "Offline reason is required.");
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.offline);
        article.setOfflineAt(LocalDateTime.now());
        article.setOfflineBy(currentOperatorName());
        article.setOfflineReason(reason);
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.offline, fromStatus, saved.getStatus(), currentOperatorName(), reason, null);
        return saved;
    }

    @Transactional
    public Article applyPublishSuccess(Long id, Long publishJobId, String operatorName) {
        Article article = getArticleById(id);
        if (!(article.getStatus() == ArticleStatus.approved || article.getStatus() == ArticleStatus.published)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved or published content can be marked as published.");
        }
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.published);
        article.setPublishedAt(LocalDateTime.now());
        article.setPublishedBy(firstNonBlank(operatorName, currentOperatorName()));
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.publish, fromStatus, saved.getStatus(), firstNonBlank(operatorName, currentOperatorName()), null, publishJobId);
        return saved;
    }

    @Transactional
    public Article applyOfflineSuccess(Long id, String reason, Long publishJobId, String operatorName) {
        Article article = getArticleById(id);
        if (article.getStatus() != ArticleStatus.published) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only published content can be marked offline.");
        }
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.offline);
        article.setOfflineAt(LocalDateTime.now());
        article.setOfflineBy(firstNonBlank(operatorName, currentOperatorName()));
        article.setOfflineReason(reason);
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.offline, fromStatus, saved.getStatus(), firstNonBlank(operatorName, currentOperatorName()), reason, publishJobId);
        return saved;
    }

    @Transactional
    public Article rollbackToApproved(Long id, Long publishJobId, String operatorName, String reason) {
        Article article = getArticleById(id);
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.approved);
        article.setPublishedAt(null);
        article.setPublishedBy(null);
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.rollback, fromStatus, saved.getStatus(), firstNonBlank(operatorName, currentOperatorName()), reason, publishJobId);
        return saved;
    }

    @Transactional
    public Article rollbackToPublished(Long id, Long publishJobId, String operatorName, String reason) {
        Article article = getArticleById(id);
        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.published);
        article.setOfflineAt(null);
        article.setOfflineBy(null);
        article.setOfflineReason(null);
        article.setPublishedAt(LocalDateTime.now());
        article.setPublishedBy(firstNonBlank(operatorName, currentOperatorName()));
        Article saved = articleRepository.save(article);
        recordLifecycleHistory(saved.getId(), ArticleLifecycleAction.rollback, fromStatus, saved.getStatus(), firstNonBlank(operatorName, currentOperatorName()), reason, publishJobId);
        return saved;
    }

    private void prepareEditablePayload(Article article) {
        if (article == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article payload is required.");
        }
        if (article.getTitle() == null || article.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article title is required.");
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
        syncCategoryName(article);
    }

    private void validateBeforeSubmitReview(Article article) {
        List<String> reasons = buildPublishCheck(article).getReasons();
        reasons.remove("content status must be approved before formal publishing");
        if (!reasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", reasons));
        }
    }

    private ArticlePublishCheckResponse buildPublishCheck(Article article) {
        ArticlePublishCheckResponse response = new ArticlePublishCheckResponse();
        response.setArticleId(article.getId());
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (article.getStatus() != ArticleStatus.approved) {
            reasons.add("content status must be approved before formal publishing");
        }
        if (article.getTitle() == null || article.getTitle().isBlank()) {
            reasons.add("title is required");
        }
        if (article.getContent() == null || article.getContent().isBlank()) {
            reasons.add("content body is required");
        }
        if (article.getSiteId() == null) {
            reasons.add("site is required");
        }
        if (article.getPrimaryCategoryId() == null) {
            reasons.add("primary category is required");
        }

        Site site = null;
        if (article.getSiteId() != null) {
            site = siteRepository.findById(article.getSiteId()).orElse(null);
            if (site == null) {
                reasons.add("site does not exist");
            } else if (!"enabled".equalsIgnoreCase(site.getStatus())) {
                reasons.add("site is not enabled");
            }
        }

        Category category = null;
        if (article.getPrimaryCategoryId() != null && article.getSiteId() != null) {
            category = categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), article.getSiteId()).orElse(null);
            if (category == null) {
                reasons.add("primary category does not exist");
            } else {
                if (!"enabled".equalsIgnoreCase(category.getStatus())) {
                    reasons.add("category is not enabled");
                }
                if (!Boolean.TRUE.equals(category.getPublicVisible())) {
                    reasons.add("category is not publicly visible");
                }
            }
        }

        Template detailTemplate = resolveDetailTemplate(article, category);
        if (detailTemplate == null) {
            reasons.add("detail template is not resolved");
        } else {
            response.setTemplateId(detailTemplate.getId());
            response.setTemplateName(detailTemplate.getName());
            if (!"active".equalsIgnoreCase(detailTemplate.getStatus())) {
                reasons.add("detail template is not active");
            }
        }

        if (article.getSummary() == null || article.getSummary().isBlank()) {
            warnings.add("summary is empty");
        }

        response.setReasons(reasons);
        response.setWarnings(warnings);
        response.setPublishable(reasons.isEmpty());
        return response;
    }

    private Template resolveDetailTemplate(Article article, Category category) {
        if (article.getSiteId() == null) {
            return null;
        }
        if (category != null && category.getDetailTemplateId() != null) {
            return templateRepository.findById(category.getDetailTemplateId()).orElse(null);
        }
        if (category != null) {
            Optional<TemplateBinding> categoryBinding = Optional.ofNullable(templateBindingRepository
                    .findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(
                            article.getSiteId(),
                            "column",
                            category.getId(),
                            "column_detail_default",
                            "active"
                    ))
                    .flatMap(bindings -> bindings.stream().findFirst());
            if (categoryBinding.isPresent()) {
                return templateRepository.findById(categoryBinding.get().getTemplateId()).orElse(null);
            }
        }
        Optional<TemplateBinding> siteBinding = Optional.ofNullable(templateBindingRepository
                .findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(
                        article.getSiteId(),
                        "site",
                        article.getSiteId(),
                        "site_detail_default",
                        "active"
                ))
                .flatMap(bindings -> bindings.stream().findFirst());
        return siteBinding.flatMap(binding -> templateRepository.findById(binding.getTemplateId())).orElse(null);
    }

    private void syncCategoryName(Article article) {
        if (article.getPrimaryCategoryId() == null) {
            if (article.getCategory() != null) {
                article.setCategory(article.getCategory().trim());
            }
            return;
        }

        Category category = categoryRepository.findById(article.getPrimaryCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected category does not exist."));

        if (article.getSiteId() == null) {
            article.setSiteId(category.getSiteId());
        }
        if (!Objects.equals(category.getSiteId(), article.getSiteId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article site and category site must match.");
        }
        article.setCategory(category.getName());
    }

    private void ensureEditable(Article article) {
        if (article.getStatus() == null || !article.getStatus().isEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or rejected content can be edited.");
        }
    }

    private void recordLifecycleHistory(Long articleId,
                                        ArticleLifecycleAction action,
                                        ArticleStatus fromStatus,
                                        ArticleStatus toStatus,
                                        String operatorName,
                                        String reason,
                                        Long publishJobId) {
        ArticleLifecycleHistory history = new ArticleLifecycleHistory();
        history.setArticleId(articleId);
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorName(firstNonBlank(operatorName, currentOperatorName()));
        history.setReason(reason);
        history.setPublishJobId(publishJobId);
        articleLifecycleHistoryRepository.save(history);
    }

    private String normalizeReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return reason.trim();
    }

    private String currentOperatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}