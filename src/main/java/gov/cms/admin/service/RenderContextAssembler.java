package gov.cms.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateVersion;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TemplateVersionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RenderContextAssembler {

    private static final String MODE_PREVIEW = "preview";
    private static final String MODE_PUBLISH = "publish";
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("sample", "column", "content");
    private static final Set<String> ALLOWED_MODES = Set.of(MODE_PREVIEW, MODE_PUBLISH);
    private static final Set<String> ALLOWED_BLOCK_TYPES = Set.of(
            "site_header", "site_footer", "breadcrumb", "rich_text",
            "hero", "nav_links", "article_list",
            "column_header", "pagination",
            "content_header", "content_meta", "content_body",
            "error_message", "quick_links"
    );
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final SiteRepository siteRepository;
    private final CategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;
    private final ObjectMapper objectMapper;

    public RenderContextAssembler(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            SiteRepository siteRepository,
            CategoryRepository categoryRepository,
            ArticleRepository articleRepository,
            ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.siteRepository = siteRepository;
        this.categoryRepository = categoryRepository;
        this.articleRepository = articleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RenderContextSnapshot assemble(RenderRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Render request is required.");
        }
        if (request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        if (request.getTemplateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "templateId is required.");
        }

        String mode = normalizeMode(request.getMode());
        Template template = templateRepository.findByIdAndSiteId(request.getTemplateId(), request.getSiteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found."));
        Site site = siteRepository.findById(request.getSiteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site not found."));
        TemplateVersion version = resolveVersion(template, request.getTemplateVersionId());
        String sourceType = normalizeSourceType(request.getSourceType(), true);
        if (sourceType == null) {
            sourceType = normalizeSourceType(template.getDefaultPreviewSource(), true);
        }
        if (sourceType == null) {
            sourceType = "sample";
        }

        String pageType = normalizePageType(request.getPageType(), true);
        if (pageType == null) {
            pageType = resolvePageType(template.getType());
        }

        List<String> warnings = new ArrayList<>();
        List<String> blockingReasons = new ArrayList<>();
        Long sourceId = request.getSourceId();
        Category previewCategory = null;
        Article previewArticle = null;

        if ("column".equals(sourceType)) {
            if (sourceId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column preview requires sourceId.");
            }
            previewCategory = categoryRepository.findByIdAndSiteId(sourceId, request.getSiteId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found."));
        } else if ("content".equals(sourceType)) {
            if (sourceId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content preview requires sourceId.");
            }
            previewArticle = articleRepository.findById(sourceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found."));
            if (previewArticle.getSiteId() != null && !Objects.equals(previewArticle.getSiteId(), request.getSiteId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article does not belong to the site.");
            }
            if (previewArticle.getPrimaryCategoryId() != null) {
                previewCategory = categoryRepository.findByIdAndSiteId(previewArticle.getPrimaryCategoryId(), request.getSiteId()).orElse(null);
            }
            if (!(previewArticle.getStatus() == ArticleStatus.approved || previewArticle.getStatus() == ArticleStatus.published)) {
                if (MODE_PUBLISH.equals(mode)) {
                    blockingReasons.add("content status is not approved or published");
                } else {
                    warnings.add("Content is not published; preview stays in controlled mode only.");
                    blockingReasons.add("content status is not approved or published");
                }
            }
        } else {
            warnings.add("Using sample render context; preview reflects contract-ready data only.");
            if ("column-list".equals(pageType) || "topic-page".equals(pageType)) {
                previewCategory = buildSampleCategory(site.getId());
            }
            if ("content-detail".equals(pageType)) {
                previewCategory = buildSampleCategory(site.getId());
                previewArticle = buildSampleArticle(site.getId(), previewCategory.getId());
                sourceId = previewArticle.getId();
            }
        }

        applyStatusRules(mode, site, template, previewCategory, previewArticle, blockingReasons, warnings);

        if (MODE_PUBLISH.equals(mode) && !blockingReasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("; ", blockingReasons));
        }

        List<Category> siteCategories = Optional.ofNullable(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(site.getId()))
                .orElse(List.of());
        Map<String, Object> siteContext = buildSiteContext(site);
        Map<String, Object> navigationContext = buildNavigationContext(siteCategories, previewCategory);
        Map<String, Object> columnContext = buildColumnContext(previewCategory, sourceType);
        Map<String, Object> contentContext = buildContentContext(previewArticle, sourceType);
        Map<String, Object> topicContext = buildTopicContext();
        Map<String, Object> renderMeta = buildRenderMeta(mode, template, version, sourceType, sourceId, pageType, blockingReasons.isEmpty());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("siteContext", siteContext);
        context.put("navigationContext", navigationContext);
        context.put("columnContext", columnContext);
        context.put("contentContext", contentContext);
        context.put("topicContext", topicContext);
        context.put("renderMeta", renderMeta);
        List<RenderContextSnapshot.LayoutSlot> layoutSlots = parseLayoutSlots(version.getLayoutSchema());
        List<RenderContextSnapshot.RenderBlock> renderBlocks = parseRenderBlocks(
                version.getBlockSchema(),
                layoutSlots,
                site,
                siteCategories,
                previewCategory,
                previewArticle,
                pageType,
                sourceType,
                mode,
                request.getIncludeArticleIds(),
                request.getExcludeArticleIds(),
                request.getOperation(),
                warnings
        );

        String pathHint = buildPathHint(pageType, previewCategory, previewArticle);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pageType", pageType);
        summary.put("publishReady", blockingReasons.isEmpty());
        summary.put("blockingReasons", blockingReasons);
        summary.put("pathHint", pathHint);
        summary.put("layoutSlotCount", layoutSlots.size());
        summary.put("renderBlockCount", renderBlocks.size());
        summary.put("schemaLengths", Map.of(
                "layoutSchema", version.getLayoutSchema() == null ? 0 : version.getLayoutSchema().length(),
                "blockSchema", version.getBlockSchema() == null ? 0 : version.getBlockSchema().length(),
                "seoSchema", version.getSeoSchema() == null ? 0 : version.getSeoSchema().length(),
                "styleSchema", version.getStyleSchema() == null ? 0 : version.getStyleSchema().length()
        ));
        summary.put("contextGroups", List.of("siteContext", "navigationContext", "columnContext", "contentContext", "topicContext", "renderMeta"));
        summary.put("contractStage", "render-ready");

        RenderContextSnapshot snapshot = new RenderContextSnapshot();
        snapshot.setTemplateId(template.getId());
        snapshot.setTemplateName(template.getName());
        snapshot.setTemplateVersionId(version.getId());
        snapshot.setTemplateType(template.getType());
        snapshot.setVersionNo(version.getVersionNo());
        snapshot.setPageType(pageType);
        snapshot.setSourceType(sourceType);
        snapshot.setSourceId(sourceId);
        snapshot.setPathHint(pathHint);
        snapshot.setLayoutSchema(version.getLayoutSchema());
        snapshot.setBlockSchema(version.getBlockSchema());
        snapshot.setSeoSchema(version.getSeoSchema());
        snapshot.setStyleSchema(version.getStyleSchema());
        snapshot.setContext(context);
        snapshot.setSummary(summary);
        snapshot.setWarnings(warnings);
        snapshot.setPublishReady(blockingReasons.isEmpty());
        snapshot.setLayoutSlots(layoutSlots);
        snapshot.setRenderBlocks(renderBlocks);
        return snapshot;
    }

    private void applyStatusRules(String mode,
                                  Site site,
                                  Template template,
                                  Category previewCategory,
                                  Article previewArticle,
                                  List<String> blockingReasons,
                                  List<String> warnings) {
        if (!"enabled".equalsIgnoreCase(site.getStatus())) {
            if (MODE_PUBLISH.equals(mode)) {
                blockingReasons.add("site status is not enabled");
            } else {
                warnings.add("Site is not enabled; rendered preview is not publish-ready.");
                blockingReasons.add("site status is not enabled");
            }
        }
        if (!"active".equals(template.getStatus())) {
            if (MODE_PUBLISH.equals(mode)) {
                blockingReasons.add("template status is not active");
            } else if ("draft".equals(template.getStatus())) {
                warnings.add("Template is in draft status; preview is available but not publish-ready.");
                blockingReasons.add("template status is draft");
            } else {
                warnings.add("Template is disabled; enable it before formal publishing.");
                blockingReasons.add("template status is disabled");
            }
        }
        if (previewCategory != null && (!"enabled".equalsIgnoreCase(previewCategory.getStatus()) || !Boolean.TRUE.equals(previewCategory.getPublicVisible()))) {
            if (MODE_PUBLISH.equals(mode)) {
                blockingReasons.add("category is not publicly available");
            } else {
                warnings.add("Category is not fully publishable; preview remains controlled.");
                blockingReasons.add("category is not publicly available");
            }
        }
        if ("content-detail".equals(resolvePageType(template.getType())) && previewArticle == null) {
            if (MODE_PUBLISH.equals(mode)) {
                blockingReasons.add("content-detail page requires content source");
            } else {
                warnings.add("Content detail preview has no content source.");
                blockingReasons.add("content-detail page requires content source");
            }
        }
    }

    private TemplateVersion resolveVersion(Template template, Long templateVersionId) {
        if (templateVersionId != null) {
            TemplateVersion version = templateVersionRepository.findById(templateVersionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template version not found."));
            if (!Objects.equals(version.getTemplateId(), template.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template version does not belong to template.");
            }
            return version;
        }
        if (template.getCurrentVersionId() != null) {
            return templateVersionRepository.findById(template.getCurrentVersionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current template version not found."));
        }
        return templateVersionRepository.findTopByTemplateIdOrderByVersionNoDesc(template.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template has no version."));
    }

    private List<RenderContextSnapshot.LayoutSlot> parseLayoutSlots(String layoutSchema) {
        JsonNode root = readJson(layoutSchema, "layoutSchema");
        JsonNode layout = root.path("layout");
        if (!layout.isArray() || layout.isEmpty()) {
            return List.of(defaultMainSlot());
        }
        Map<String, RenderContextSnapshot.LayoutSlot> dedup = new LinkedHashMap<>();
        for (JsonNode item : layout) {
            String slotName = normalizeText(item.path("slot").asText(null));
            if (slotName == null) {
                slotName = "main";
            }
            RenderContextSnapshot.LayoutSlot slot = new RenderContextSnapshot.LayoutSlot();
            slot.setName(slotName);
            slot.setLabel(normalizeText(item.path("name").asText(null)) == null ? slotName : item.path("name").asText());
            slot.setProps(toMap(item.path("props")));
            dedup.putIfAbsent(slotName, slot);
        }
        if (dedup.isEmpty()) {
            return List.of(defaultMainSlot());
        }
        if (!dedup.containsKey("main")) {
            dedup.put("main", defaultMainSlot());
        }
        return new ArrayList<>(dedup.values());
    }

    private List<RenderContextSnapshot.RenderBlock> parseRenderBlocks(String blockSchema,
                                                                      List<RenderContextSnapshot.LayoutSlot> layoutSlots,
                                                                      Site site,
                                                                      List<Category> siteCategories,
                                                                      Category previewCategory,
                                                                      Article previewArticle,
                                                                      String pageType,
                                                                      String sourceType,
                                                                      String mode,
                                                                      List<Long> includeArticleIds,
                                                                      List<Long> excludeArticleIds,
                                                                      String operation,
                                                                      List<String> warnings) {
        JsonNode root = readJson(blockSchema, "blockSchema");
        JsonNode blocksNode = root.path("blocks");
        if (!blocksNode.isArray() || blocksNode.isEmpty()) {
            return List.of();
        }
        Set<String> slotNames = layoutSlots.stream().map(RenderContextSnapshot.LayoutSlot::getName).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RenderContextSnapshot.RenderBlock> blocks = new ArrayList<>();
        int index = 0;
        for (JsonNode item : blocksNode) {
            index++;
            String type = normalizeText(item.path("type").asText(null));
            if (type == null) {
                handleUnsupported(mode, warnings, "Encountered block without type.");
                continue;
            }
            if (!ALLOWED_BLOCK_TYPES.contains(type)) {
                handleUnsupported(mode, warnings, "Unsupported block type: " + type);
                continue;
            }
            String slot = normalizeText(item.path("slot").asText(null));
            if (slot == null) {
                slot = "main";
            }
            if (!slotNames.contains(slot)) {
                warnings.add("Block " + type + " targets unknown slot " + slot + "; moved to main.");
                slot = "main";
            }
            Map<String, Object> props = toMap(item.path("props"));
            RenderContextSnapshot.RenderBlock block = new RenderContextSnapshot.RenderBlock();
            block.setId(normalizeText(item.path("id").asText(null)) == null ? type + "-" + index : item.path("id").asText());
            block.setType(type);
            block.setSlot(slot);
            block.setProps(props);
            block.setData(resolveBlockData(type, props, site, siteCategories, previewCategory, previewArticle, pageType, sourceType, mode, includeArticleIds, excludeArticleIds, operation));
            blocks.add(block);
        }
        return blocks;
    }

    private Object resolveBlockData(String type,
                                    Map<String, Object> props,
                                    Site site,
                                    List<Category> siteCategories,
                                    Category previewCategory,
                                    Article previewArticle,
                                    String pageType,
                                    String sourceType,
                                    String mode,
                                    List<Long> includeArticleIds,
                                    List<Long> excludeArticleIds,
                                    String operation) {
        return switch (type) {
            case "site_header" -> Map.of(
                    "siteName", site.getName(),
                    "siteCode", site.getCode(),
                    "navigation", primaryNavigation(siteCategories)
            );
            case "site_footer" -> Map.of(
                    "siteName", site.getName(),
                    "domain", site.getDomain() == null ? "" : site.getDomain(),
                    "year", LocalDateTime.now().getYear()
            );
            case "hero" -> Map.of(
                    "title", firstNonBlank(asString(props.get("title")), site.getName()),
                    "subtitle", firstNonBlank(asString(props.get("subtitle")), site.getDescription())
            );
            case "nav_links", "quick_links" -> primaryNavigation(siteCategories);
            case "breadcrumb" -> buildBreadcrumb(previewCategory, siteCategories);
            case "rich_text" -> Map.of("html", sanitizeHtml(firstNonBlank(asString(props.get("html")), asString(props.get("content")))));
            case "column_header" -> Map.of(
                    "name", previewCategory == null ? "" : nullToEmpty(previewCategory.getName()),
                    "description", previewCategory == null ? "" : nullToEmpty(previewCategory.getDescription()),
                    "fullPath", previewCategory == null ? "" : nullToEmpty(previewCategory.getFullPath())
            );
            case "article_list" -> buildArticleList(site, siteCategories, previewCategory, previewArticle, pageType, sourceType, mode, operation, includeArticleIds, excludeArticleIds, props);
            case "pagination" -> buildPaginationData(props);
            case "content_header" -> Map.of(
                    "title", previewArticle == null ? "" : nullToEmpty(previewArticle.getTitle()),
                    "summary", previewArticle == null ? "" : nullToEmpty(previewArticle.getSummary())
            );
            case "content_meta" -> Map.of(
                    "author", previewArticle == null ? "" : nullToEmpty(previewArticle.getAuthor()),
                    "views", previewArticle == null ? 0 : Optional.ofNullable(previewArticle.getViews()).orElse(0),
                    "updatedAt", previewArticle == null || previewArticle.getUpdatedAt() == null ? "" : previewArticle.getUpdatedAt().toString(),
                    "sourceType", sourceType
            );
            case "content_body" -> Map.of(
                    "html", sanitizeHtml(previewArticle == null ? "" : previewArticle.getContent())
            );
            case "error_message" -> Map.of(
                    "title", firstNonBlank(asString(props.get("title")), "Page Not Found"),
                    "message", firstNonBlank(asString(props.get("message")), "The requested page is not available at the moment.")
            );
            default -> Collections.emptyMap();
        };
    }

    private List<Map<String, Object>> buildArticleList(Site site,
                                                       List<Category> siteCategories,
                                                       Category previewCategory,
                                                       Article previewArticle,
                                                       String pageType,
                                                       String sourceType,
                                                       String mode,
                                                       String operation,
                                                       List<Long> includeArticleIds,
                                                       List<Long> excludeArticleIds,
                                                       Map<String, Object> props) {
        int size = clamp(parseInt(props.get("size"), 10), 1, 20);
        List<Article> articles;
        if ("column-list".equals(pageType) && previewCategory != null && previewCategory.getId() != null && previewCategory.getId() > 0) {
            articles = articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(
                    site.getId(),
                    previewCategory.getId(),
                    ArticleStatus.published,
                    PageRequest.of(0, size)
            );
        } else {
            articles = articleRepository.searchArticles(null, null, ArticleStatus.published, site.getId(), null, PageRequest.of(0, size)).getContent();
        }
        if (MODE_PUBLISH.equals(mode)) {
            if (includeArticleIds != null && !includeArticleIds.isEmpty()) {
                List<Article> includedArticles = articleRepository.findAllById(includeArticleIds).stream()
                        .filter(article -> Objects.equals(article.getSiteId(), site.getId()))
                        .filter(article -> article.getStatus() == ArticleStatus.approved || article.getStatus() == ArticleStatus.published)
                        .filter(article -> previewCategory == null || Objects.equals(article.getPrimaryCategoryId(), previewCategory.getId()))
                        .toList();
                Map<Long, Article> merged = new LinkedHashMap<>();
                for (Article article : articles) {
                    merged.put(article.getId(), article);
                }
                for (Article article : includedArticles) {
                    merged.put(article.getId(), article);
                }
                articles = new ArrayList<>(merged.values());
            }
            if (excludeArticleIds != null && !excludeArticleIds.isEmpty()) {
                articles = articles.stream().filter(article -> !excludeArticleIds.contains(article.getId())).toList();
            }
        }
        if (articles.isEmpty() && MODE_PREVIEW.equals(mode)) {
            Category category = previewCategory == null ? buildSampleCategory(site.getId()) : previewCategory;
            articles = List.of(buildSampleArticle(site.getId(), category.getId()));
        }
        Map<Long, Category> categoryIndex = siteCategories.stream().collect(Collectors.toMap(Category::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        return articles.stream().map(article -> toArticleCard(article, categoryIndex)).collect(Collectors.toList());
    }

    private Map<String, Object> buildPaginationData(Map<String, Object> props) {
        int pageSize = clamp(parseInt(props.get("size"), 10), 1, 50);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentPage", 1);
        data.put("pageSize", pageSize);
        data.put("totalPages", 1);
        data.put("hasPrevious", false);
        data.put("hasNext", false);
        return data;
    }

    private Map<String, Object> toArticleCard(Article article, Map<Long, Category> categoryIndex) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", article.getId());
        card.put("title", article.getTitle());
        card.put("summary", nullToEmpty(article.getSummary()));
        card.put("author", nullToEmpty(article.getAuthor()));
        card.put("createdAt", article.getCreatedAt() == null ? "" : article.getCreatedAt().toString());
        card.put("path", resolveArticlePath(article, categoryIndex));
        return card;
    }

    private String resolveArticlePath(Article article, Map<Long, Category> categoryIndex) {
        Category category = article.getPrimaryCategoryId() == null ? null : categoryIndex.get(article.getPrimaryCategoryId());
        if (category != null && category.getFullPath() != null) {
            return category.getFullPath() + "/" + article.getId() + ".html";
        }
        return "/content/" + article.getId() + ".html";
    }

    private void handleUnsupported(String mode, List<String> warnings, String message) {
        if (MODE_PUBLISH.equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        warnings.add(message);
    }

    private RenderContextSnapshot.LayoutSlot defaultMainSlot() {
        RenderContextSnapshot.LayoutSlot slot = new RenderContextSnapshot.LayoutSlot();
        slot.setName("main");
        slot.setLabel("main");
        slot.setProps(new LinkedHashMap<>());
        return slot;
    }

    private Map<String, Object> buildSiteContext(Site site) {
        Map<String, Object> siteContext = new LinkedHashMap<>();
        siteContext.put("id", site.getId());
        siteContext.put("name", site.getName());
        siteContext.put("code", site.getCode());
        siteContext.put("domain", site.getDomain());
        siteContext.put("organizationId", site.getOrganizationId());
        siteContext.put("description", site.getDescription());
        siteContext.put("status", site.getStatus());
        return siteContext;
    }

    private Map<String, Object> buildNavigationContext(List<Category> siteCategories, Category previewCategory) {
        Map<String, Object> navigationContext = new LinkedHashMap<>();
        navigationContext.put("primaryNavigation", primaryNavigation(siteCategories));
        navigationContext.put("breadcrumb", buildBreadcrumb(previewCategory, siteCategories));
        return navigationContext;
    }
    private List<Map<String, Object>> primaryNavigation(List<Category> siteCategories) {
        return siteCategories.stream()
                .filter(category -> "enabled".equalsIgnoreCase(category.getStatus()))
                .filter(category -> Boolean.TRUE.equals(category.getNavVisible()))
                .filter(category -> Boolean.TRUE.equals(category.getPublicVisible()))
                .filter(category -> category.getParentId() == null)
                .limit(12)
                .map(category -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", category.getId());
                    item.put("name", category.getName());
                    item.put("path", category.getFullPath());
                    item.put("slug", category.getSlug());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildBreadcrumb(Category previewCategory, List<Category> siteCategories) {
        if (previewCategory == null) {
            return List.of();
        }
        Map<Long, Category> index = siteCategories.stream().collect(Collectors.toMap(Category::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<Map<String, Object>> breadcrumb = new ArrayList<>();
        Category cursor = previewCategory;
        while (cursor != null) {
            if (Boolean.TRUE.equals(cursor.getBreadcrumbVisible())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", cursor.getId());
                item.put("name", cursor.getName());
                item.put("path", cursor.getFullPath());
                breadcrumb.add(item);
            }
            cursor = cursor.getParentId() == null ? null : index.get(cursor.getParentId());
        }
        Collections.reverse(breadcrumb);
        return breadcrumb;
    }

    private Map<String, Object> buildColumnContext(Category previewCategory, String sourceType) {
        Map<String, Object> columnContext = new LinkedHashMap<>();
        columnContext.put("sourceType", sourceType);
        if (previewCategory == null) {
            columnContext.put("available", false);
            columnContext.put("message", "Column context is not loaded.");
            return columnContext;
        }
        columnContext.put("available", true);
        columnContext.put("id", previewCategory.getId());
        columnContext.put("name", previewCategory.getName());
        columnContext.put("code", previewCategory.getCode());
        columnContext.put("slug", previewCategory.getSlug());
        columnContext.put("fullPath", previewCategory.getFullPath());
        columnContext.put("level", previewCategory.getLevel());
        columnContext.put("description", previewCategory.getDescription());
        columnContext.put("aggregationMode", previewCategory.getAggregationMode());
        columnContext.put("seoTitle", previewCategory.getSeoTitle());
        columnContext.put("seoKeywords", previewCategory.getSeoKeywords());
        columnContext.put("seoDescription", previewCategory.getSeoDescription());
        return columnContext;
    }

    private Map<String, Object> buildContentContext(Article previewArticle, String sourceType) {
        Map<String, Object> contentContext = new LinkedHashMap<>();
        contentContext.put("sourceType", sourceType);
        if (previewArticle == null) {
            contentContext.put("available", false);
            contentContext.put("message", "Content context is not loaded.");
            return contentContext;
        }
        contentContext.put("available", true);
        contentContext.put("id", previewArticle.getId());
        contentContext.put("title", previewArticle.getTitle());
        contentContext.put("summary", previewArticle.getSummary());
        contentContext.put("content", previewArticle.getContent());
        contentContext.put("category", previewArticle.getCategory());
        contentContext.put("author", previewArticle.getAuthor());
        contentContext.put("status", previewArticle.getStatus());
        contentContext.put("views", previewArticle.getViews());
        contentContext.put("primaryCategoryId", previewArticle.getPrimaryCategoryId());
        contentContext.put("createdAt", previewArticle.getCreatedAt() == null ? null : previewArticle.getCreatedAt().toString());
        contentContext.put("updatedAt", previewArticle.getUpdatedAt() == null ? null : previewArticle.getUpdatedAt().toString());
        return contentContext;
    }

    private Map<String, Object> buildTopicContext() {
        Map<String, Object> topicContext = new LinkedHashMap<>();
        topicContext.put("available", false);
        topicContext.put("supported", false);
        topicContext.put("message", "Topic rendering is not supported yet.");
        return topicContext;
    }

    private Map<String, Object> buildRenderMeta(String mode,
                                                Template template,
                                                TemplateVersion version,
                                                String sourceType,
                                                Long sourceId,
                                                String pageType,
                                                boolean publishReady) {
        Map<String, Object> renderMeta = new LinkedHashMap<>();
        renderMeta.put("generatedAt", LocalDateTime.now().toString());
        renderMeta.put("mode", mode);
        renderMeta.put("contractStage", "render-ready");
        renderMeta.put("templateId", template.getId());
        renderMeta.put("templateVersionId", version.getId());
        renderMeta.put("templateVersionNo", version.getVersionNo());
        renderMeta.put("templateStatus", template.getStatus());
        renderMeta.put("pageType", pageType);
        renderMeta.put("sourceType", sourceType);
        renderMeta.put("sourceId", sourceId);
        renderMeta.put("publishReady", publishReady);
        return renderMeta;
    }

    private String buildPathHint(String pageType, Category previewCategory, Article previewArticle) {
        return switch (pageType) {
            case "home" -> "/index.html";
            case "column-list" -> previewCategory != null && previewCategory.getFullPath() != null ? previewCategory.getFullPath() + "/index.html" : "/columns/index.html";
            case "content-detail" -> previewCategory != null && previewCategory.getFullPath() != null && previewArticle != null
                    ? previewCategory.getFullPath() + "/" + previewArticle.getId() + ".html"
                    : "/content/detail.html";
            case "error-404" -> "/404.html";
            default -> "/index.html";
        };
    }

    private String resolvePageType(String templateType) {
        return switch (templateType) {
            case "home" -> "home";
            case "column_list" -> "column-list";
            case "content_detail" -> "content-detail";
            case "topic_page" -> "topic-page";
            case "not_found" -> "error-404";
            default -> templateType;
        };
    }

    private Category buildSampleCategory(Long siteId) {
        Category category = new Category();
        category.setId(0L);
        category.setSiteId(siteId);
        category.setName("Sample Column");
        category.setCode("sample-column");
        category.setSlug("sample-column");
        category.setFullPath("/sample-column");
        category.setLevel(1);
        category.setAggregationMode("manual");
        category.setStatus("enabled");
        category.setNavVisible(true);
        category.setBreadcrumbVisible(true);
        category.setPublicVisible(true);
        category.setDescription("Sample category for controlled preview.");
        return category;
    }

    private Article buildSampleArticle(Long siteId, Long categoryId) {
        Article article = new Article();
        article.setId(0L);
        article.setSiteId(siteId);
        article.setPrimaryCategoryId(categoryId);
        article.setTitle("Sample content title");
        article.setSummary("This is a sample summary for controlled preview and render testing.");
        article.setContent("<p>This is sample body content for render testing.</p>");
        article.setCategory("Sample Category");
        article.setAuthor("system");
        article.setStatus(ArticleStatus.published);
        article.setViews(128);
        article.setCreatedAt(LocalDateTime.now().minusDays(1));
        article.setUpdatedAt(LocalDateTime.now());
        return article;
    }
    private JsonNode readJson(String json, String label) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is not valid JSON.");
        }
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private String normalizeSourceType(String sourceType, boolean allowNull) {
        String normalized = normalizeText(sourceType);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceType is required.");
        }
        if (!ALLOWED_SOURCE_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sourceType.");
        }
        return normalized;
    }

    private String normalizeMode(String mode) {
        String normalized = normalizeText(mode);
        if (normalized == null) {
            return MODE_PREVIEW;
        }
        if (!ALLOWED_MODES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported render mode.");
        }
        return normalized;
    }

    private String normalizePageType(String pageType, boolean allowNull) {
        String normalized = normalizeText(pageType);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageType is required.");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeText(first);
        if (normalizedFirst != null) {
            return normalizedFirst;
        }
        String normalizedSecond = normalizeText(second);
        return normalizedSecond == null ? "" : normalizedSecond;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sanitizeHtml(String html) {
        String value = html == null ? "" : html;
        value = value.replaceAll("(?is)<script.*?>.*?</script>", "");
        value = value.replaceAll("(?is)<iframe.*?>.*?</iframe>", "");
        value = value.replaceAll("(?i)on[a-z]+\\s*=\\s*(['\"]).*?\\1", "");
        value = value.replaceAll("(?i)javascript:", "");
        return value;
    }
}
