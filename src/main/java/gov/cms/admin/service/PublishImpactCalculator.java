package gov.cms.admin.service;

import gov.cms.admin.dto.PublishRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.NavigationItem;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.NavigationItemRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TopicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class PublishImpactCalculator {

    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final TemplateRepository templateRepository;
    private final TemplateBindingRepository templateBindingRepository;
    private final SiteRepository siteRepository;
    private final NavigationItemRepository navigationItemRepository;
    private final TopicRepository topicRepository;

    public PublishImpactCalculator(ArticleRepository articleRepository,
                                   CategoryRepository categoryRepository,
                                   TemplateRepository templateRepository,
                                   TemplateBindingRepository templateBindingRepository,
                                   SiteRepository siteRepository,
                                   NavigationItemRepository navigationItemRepository,
                                   TopicRepository topicRepository) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateBindingRepository = templateBindingRepository;
        this.siteRepository = siteRepository;
        this.navigationItemRepository = navigationItemRepository;
        this.topicRepository = topicRepository;
    }

    public ImpactPlan calculate(PublishRequest request) {
        ImpactPlan plan = new ImpactPlan();
        String unitType = normalizeUnitType(request.getUnitType());
        String mode = normalizeMode(request.getMode(), unitType);
        Site site = siteRepository.findById(request.getSiteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site not found."));

        for (Long unitId : request.getUnitIds()) {
            switch (unitType) {
                case "content" -> collectContentImpacts(site, unitId, mode, plan);
                case "category" -> collectCategoryImpacts(site, unitId, plan);
                case "template" -> collectTemplateImpacts(site, unitId, plan);
                case "site" -> collectSiteImpacts(site, plan);
                case "navigation" -> collectNavigationImpacts(site, unitId, plan);
                case "topic" -> collectTopicImpacts(site, unitId, plan);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported publish unitType.");
            }
        }

        plan.setItems(new ArrayList<>(dedupe(plan.getItems()).values()));
        return plan;
    }

    public String normalizeUnitType(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitType is required.");
        }
        String normalized = unitType.trim().toLowerCase();
        if (!List.of("content", "category", "template", "site", "navigation", "topic").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported unitType.");
        }
        return normalized;
    }

    public String normalizeMode(String mode, String unitType) {
        String normalized = mode == null || mode.isBlank() ? defaultMode(unitType) : mode.trim().toLowerCase();
        if (!List.of("incremental", "full", "offline", "rollback").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported publish mode.");
        }
        if ("content".equals(unitType) && !("incremental".equals(normalized) || "offline".equals(normalized))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content only supports incremental or offline mode.");
        }
        if ("site".equals(unitType) && !"full".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site publish only supports full mode.");
        }
        if (("category".equals(unitType) || "template".equals(unitType) || "navigation".equals(unitType) || "topic".equals(unitType)) && !"incremental".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unitType + " publish only supports incremental mode.");
        }
        return normalized;
    }

    private String defaultMode(String unitType) {
        return "site".equals(unitType) ? "full" : "incremental";
    }

    private void collectContentImpacts(Site site, Long articleId, String mode, ImpactPlan plan) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article not found."));
        if (!Objects.equals(article.getSiteId(), site.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article does not belong to site.");
        }
        Category category = article.getPrimaryCategoryId() == null ? null : categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), site.getId()).orElse(null);

        plan.addItem(createImpact("content-detail", "content", article.getId(), "content", article.getId(), articlePath(article, category), "offline".equals(mode) ? "delete" : "update", "内容详情页"));
        if (category != null) {
            plan.addItem(createImpact("column-list", "column", category.getId(), "category", category.getId(), categoryPath(category), "update", "栏目页"));
        plan.addItem(createImpact("search-index", "category", category.getId(), "category", category.getId(), "/search-index/site-" + site.getId(), "update", "搜索索引"));
        }
        plan.addItem(createImpact("home", "sample", null, "site", site.getId(), "/index.html", "update", "站点首页"));
        plan.addItem(createImpact("search-index", "site", site.getId(), "site", site.getId(), "/search-index/site-" + site.getId(), "update", "搜索索引"));
    }

    private void collectCategoryImpacts(Site site, Long categoryId, ImpactPlan plan) {
        Category category = categoryRepository.findByIdAndSiteId(categoryId, site.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found."));
        plan.addItem(createImpact("column-list", "column", category.getId(), "category", category.getId(), categoryPath(category), "update", "栏目页"));
        plan.addItem(createImpact("search-index", "category", category.getId(), "category", category.getId(), "/search-index/site-" + site.getId(), "update", "搜索索引"));
        plan.addItem(createImpact("home", "sample", null, "site", site.getId(), "/index.html", "update", "站点首页"));
        plan.addItem(createImpact("search-index", "site", site.getId(), "site", site.getId(), "/search-index/site-" + site.getId(), "update", "搜索索引"));
        for (Article article : articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(site.getId(), category.getId(), ArticleStatus.published)) {
            plan.addItem(createImpact("content-detail", "content", article.getId(), "content", article.getId(), articlePath(article, category), "update", "栏目关联详情页"));
        }
    }

    private void collectTemplateImpacts(Site site, Long templateId, ImpactPlan plan) {
        Template template = templateRepository.findByIdAndSiteId(templateId, site.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template not found."));
        List<TemplateBinding> bindings = templateBindingRepository.findByTemplateIdAndSiteId(template.getId(), site.getId());
        if (bindings.isEmpty()) {
            plan.addWarning("Template has no bindings; no publish impacts generated.");
            return;
        }
        for (TemplateBinding binding : bindings) {
            if (!"active".equalsIgnoreCase(binding.getStatus())) {
                continue;
            }
            switch (binding.getBindingSlot()) {
                case "site_home" -> plan.addItem(createImpact("home", "sample", null, "site", site.getId(), "/index.html", "update", "首页模板影响"));
                case "site_404" -> plan.addItem(createImpact("error-404", "sample", null, "site", site.getId(), "/404.html", "update", "404 页模板影响"));
                case "column_list" -> {
                    if (!"column".equals(binding.getTargetType())) {
                        plan.addWarning("Unsupported column_list binding target: " + binding.getTargetType());
                    } else {
                        Category category = categoryRepository.findByIdAndSiteId(binding.getTargetId(), site.getId()).orElse(null);
                        if (category != null) {
                            plan.addItem(createImpact("column-list", "column", category.getId(), "category", category.getId(), categoryPath(category), "update", "栏目模板影响"));
                        }
                    }
                }
                case "column_detail_default" -> {
                    if ("column".equals(binding.getTargetType())) {
                        Category category = categoryRepository.findByIdAndSiteId(binding.getTargetId(), site.getId()).orElse(null);
                        if (category != null) {
                            for (Article article : articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(site.getId(), category.getId(), ArticleStatus.published)) {
                                plan.addItem(createImpact("content-detail", "content", article.getId(), "content", article.getId(), articlePath(article, category), "update", "详情模板影响"));
                            }
                        }
                    } else if ("site".equals(binding.getTargetType())) {
                        for (Article article : articleRepository.findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(site.getId(), ArticleStatus.published)) {
                            Category category = article.getPrimaryCategoryId() == null ? null : categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), site.getId()).orElse(null);
                            plan.addItem(createImpact("content-detail", "content", article.getId(), "content", article.getId(), articlePath(article, category), "update", "站点默认详情模板影响"));
                        }
                    } else {
                        plan.addWarning("Unsupported detail binding target: " + binding.getTargetType());
                    }
                }
                case "topic_page" -> { if ("topic".equals(binding.getTargetType())) { Topic topic = topicRepository.findByIdAndSiteId(binding.getTargetId(), site.getId()).orElse(null); if (topic != null) { plan.addItem(createImpact("topic-page", "topic", topic.getId(), "template", template.getId(), topicPath(topic), "update", "专题页模板影响")); } } else { plan.addWarning("Unsupported topic_page binding target: " + binding.getTargetType()); } }
                default -> plan.addWarning("Unsupported binding slot: " + binding.getBindingSlot());
            }
        }
    }

    private void collectSiteImpacts(Site site, ImpactPlan plan) {
        plan.addItem(createImpact("home", "sample", null, "site", site.getId(), "/index.html", "update", "站点首页"));
        plan.addItem(createImpact("search-index", "site", site.getId(), "site", site.getId(), "/search-index/site-" + site.getId(), "update", "搜索索引"));
        plan.addItem(createImpact("error-404", "sample", null, "site", site.getId(), "/404.html", "update", "站点404页"));
        for (Category category : categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(site.getId())) {
            if ("enabled".equalsIgnoreCase(category.getStatus()) && Boolean.TRUE.equals(category.getPublicVisible())) {
                plan.addItem(createImpact("column-list", "column", category.getId(), "category", category.getId(), categoryPath(category), "update", "站点栏目页"));
            }
        }
        for (Article article : articleRepository.findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(site.getId(), ArticleStatus.published)) {
            Category category = article.getPrimaryCategoryId() == null ? null : categoryRepository.findByIdAndSiteId(article.getPrimaryCategoryId(), site.getId()).orElse(null);
            plan.addItem(createImpact("content-detail", "content", article.getId(), "content", article.getId(), articlePath(article, category), "update", "站点详情页"));
        }
    }

    private void collectNavigationImpacts(Site site, Long navigationId, ImpactPlan plan) {
        NavigationItem item = navigationItemRepository.findByIdAndSiteId(navigationId, site.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation item not found."));
        plan.addItem(createImpact("home", "sample", null, "navigation", item.getId(), "/index.html", "update", "导航变更影响首页"));
        if ("category".equals(item.getTargetType()) && item.getTargetId() != null) {
            Category category = categoryRepository.findByIdAndSiteId(item.getTargetId(), site.getId()).orElse(null);
            if (category != null) {
                plan.addItem(createImpact("column-list", "column", category.getId(), "navigation", item.getId(), categoryPath(category), "update", "导航变更影响栏目入口"));
            }
        }
        if ("topic".equals(item.getTargetType()) && item.getTargetId() != null) {
            Topic topic = topicRepository.findByIdAndSiteId(item.getTargetId(), site.getId()).orElse(null);
            if (topic != null) {
                plan.addItem(createImpact("topic-page", "topic", topic.getId(), "navigation", item.getId(), topicPath(topic), "update", "导航引用专题影响"));
            }
        }
    }

    private void collectTopicImpacts(Site site, Long topicId, ImpactPlan plan) {
        Topic topic = topicRepository.findByIdAndSiteId(topicId, site.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic not found."));
        plan.addItem(createImpact("topic-page", "topic", topic.getId(), "topic", topic.getId(), topicPath(topic), "update", "专题页"));
        plan.addItem(createImpact("search-index", "topic", topic.getId(), "topic", topic.getId(), "/search-index/site-" + site.getId(), "update", "搜索索引"));
        if (Boolean.TRUE.equals(topic.getNavVisible())) {
            plan.addItem(createImpact("home", "sample", null, "topic", topic.getId(), "/index.html", "update", "首页专题位"));
        }
    }

    private PublishImpactItem createImpact(String pageType, String sourceType, Long sourceId, String objectType, Long objectId, String path, String action, String summary) {
        PublishImpactItem item = new PublishImpactItem();
        item.setPageType(pageType);
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setObjectType(objectType);
        item.setObjectId(objectId);
        item.setPath(path);
        item.setAction(action);
        item.setSummary(summary);
        return item;
    }

    private String articlePath(Article article, Category category) {
        if (category != null && category.getFullPath() != null && !category.getFullPath().isBlank()) {
            return category.getFullPath() + "/" + article.getId() + ".html";
        }
        return "/content/" + article.getId() + ".html";
    }

    private String categoryPath(Category category) {
        if (category.getFullPath() != null && !category.getFullPath().isBlank()) {
            return category.getFullPath() + "/index.html";
        }
        return "/columns/" + category.getId() + "/index.html";
    }

    private String topicPath(Topic topic) {
        return "/topics/" + topic.getSlug() + "/index.html";
    }

    private Map<String, PublishImpactItem> dedupe(List<PublishImpactItem> items) {
        Map<String, PublishImpactItem> deduped = new LinkedHashMap<>();
        for (PublishImpactItem item : items) {
            deduped.put(item.getAction() + "|" + item.getPath(), item);
        }
        return deduped;
    }

    public static class ImpactPlan {
        private List<PublishImpactItem> items = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public List<PublishImpactItem> getItems() { return items; }
        public void setItems(List<PublishImpactItem> items) { this.items = items; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public void addItem(PublishImpactItem item) { this.items.add(item); }
        public void addWarning(String warning) { this.warnings.add(warning); }
    }
}


