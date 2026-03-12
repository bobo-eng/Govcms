package gov.cms.admin.service;

import gov.cms.admin.dto.CategoryImpactResponse;
import gov.cms.admin.dto.CategoryMoveRequest;
import gov.cms.admin.dto.CategoryRequest;
import gov.cms.admin.dto.CategorySortRequest;
import gov.cms.admin.dto.CategoryStatusUpdateRequest;
import gov.cms.admin.dto.CategoryTreeNode;
import gov.cms.admin.entity.Category;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_-]+$");
    private static final Set<String> ALLOWED_TYPES = Set.of("channel", "single_page", "external_link");
    private static final Set<String> ALLOWED_STATUS = Set.of("enabled", "disabled");
    private static final Set<String> ALLOWED_AGGREGATION = Set.of("manual", "inherit_children");
    private static final int MAX_LEVEL = 4;

    private final CategoryRepository categoryRepository;
    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           SiteRepository siteRepository,
                           ArticleRepository articleRepository) {
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeNode> getCategoryTree(Long siteId, String keyword, String status) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        ensureSiteExists(siteId);
        List<Category> categories = categoryRepository.searchCategories(siteId, null, normalizeText(keyword), normalizeStatus(status, true));
        return buildTree(categories);
    }

    @Transactional(readOnly = true)
    public List<Category> getCategories(Long siteId, Long parentId, String keyword, String status) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        ensureSiteExists(siteId);
        return categoryRepository.searchCategories(siteId, parentId, normalizeText(keyword), normalizeStatus(status, true));
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id, Long siteId) {
        if (siteId != null) {
            return categoryRepository.findByIdAndSiteId(id, siteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "栏目不存在"));
        }
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "栏目不存在"));
    }

    @Transactional
    public Category createCategory(CategoryRequest request) {
        Category normalized = normalizeRequest(request, null, null);
        if (categoryRepository.existsBySiteIdAndCodeIgnoreCase(normalized.getSiteId(), normalized.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目编码已存在");
        }
        if (categoryRepository.existsBySiteIdAndFullPath(normalized.getSiteId(), normalized.getFullPath())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目路径已存在");
        }
        if (categoryRepository.existsSiblingName(normalized.getSiteId(), normalized.getParentId(), normalized.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同级栏目名称已存在");
        }
        return categoryRepository.save(normalized);
    }

    @Transactional
    public Category updateCategory(Long id, CategoryRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        List<Category> siteCategories = categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(request.getSiteId());
        Category existing = getCategoryById(id, request.getSiteId());
        Category normalized = normalizeRequest(request, existing, siteCategories);
        if (categoryRepository.existsBySiteIdAndCodeIgnoreCaseAndIdNot(normalized.getSiteId(), normalized.getCode(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目编码已存在");
        }
        if (categoryRepository.existsBySiteIdAndFullPathAndIdNot(normalized.getSiteId(), normalized.getFullPath(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目路径已存在");
        }
        if (categoryRepository.existsSiblingNameExcludingId(normalized.getSiteId(), normalized.getParentId(), normalized.getName(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同级栏目名称已存在");
        }

        boolean requiresRefresh = !Objects.equals(existing.getParentId(), normalized.getParentId())
                || !Objects.equals(existing.getSlug(), normalized.getSlug());

        applyMutableFields(existing, normalized);
        Category saved = categoryRepository.save(existing);
        if (requiresRefresh) {
            refreshDescendants(saved.getSiteId(), saved.getId());
        }
        return saved;
    }

    @Transactional
    public Category updateSort(Long id, CategorySortRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        Category category = getCategoryById(id, request.getSiteId());
        category.setSortOrder(normalizeSortOrder(request.getSortOrder()));
        return categoryRepository.save(category);
    }

    @Transactional
    public Category moveCategory(Long id, CategoryMoveRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        Category category = getCategoryById(id, request.getSiteId());
        List<Category> siteCategories = categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(category.getSiteId());
        Map<Long, Category> categoryMap = siteCategories.stream().collect(Collectors.toMap(Category::getId, item -> item));
        Long targetParentId = request.getTargetParentId();
        if (Objects.equals(category.getId(), targetParentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目不能移动到自身下");
        }

        Category targetParent = null;
        if (targetParentId != null) {
            targetParent = categoryMap.get(targetParentId);
            if (targetParent == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标父栏目不存在");
            }
            if (!Objects.equals(targetParent.getSiteId(), category.getSiteId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标父栏目不属于当前站点");
            }
            if (isDescendant(category.getId(), targetParentId, categoryMap)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目不能移动到自己的子栏目下");
            }
        }

        int newLevel = targetParent == null ? 1 : targetParent.getLevel() + 1;
        int maxChildDepth = calculateMaxDepth(category.getId(), siteCategories, 0);
        if (newLevel + maxChildDepth - 1 > MAX_LEVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "移动后栏目层级超过限制");
        }

        category.setParentId(targetParentId);
        category.setLevel(newLevel);
        category.setFullPath(buildFullPath(targetParent == null ? null : targetParent.getFullPath(), category.getSlug()));
        Category saved = categoryRepository.save(category);
        refreshDescendants(saved.getSiteId(), saved.getId());
        return saved;
    }

    @Transactional
    public Category updateStatus(Long id, CategoryStatusUpdateRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        Category category = getCategoryById(id, request.getSiteId());
        category.setStatus(normalizeStatus(request.getStatus(), false));
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public CategoryImpactResponse getImpact(Long id, Long siteId) {
        Category category = getCategoryById(id, siteId);
        List<Category> siteCategories = categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(category.getSiteId());
        List<Category> subtree = collectSubtree(category.getId(), siteCategories);
        Set<Long> categoryIds = subtree.stream().map(Category::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        long directArticleCount = articleRepository.countByPrimaryCategoryId(category.getId());
        long relatedArticleCount = categoryIds.isEmpty() ? 0L : articleRepository.countByPrimaryCategoryIdIn(categoryIds);
        boolean hasChildren = subtree.size() > 1;

        CategoryImpactResponse response = new CategoryImpactResponse();
        response.setCategoryId(category.getId());
        response.setCategoryName(category.getName());
        response.setFullPath(category.getFullPath());
        response.setSubtreeCount(Math.max(subtree.size() - 1, 0));
        response.setRelatedArticleCount(relatedArticleCount);
        response.setCanDelete(!hasChildren && directArticleCount == 0);
        response.setCanMove(true);
        response.setImpactedPaths(subtree.stream().map(Category::getFullPath).filter(Objects::nonNull).limit(8).collect(Collectors.toList()));

        List<String> warnings = new ArrayList<>();
        if (hasChildren) {
            warnings.add("当前栏目下仍存在子栏目，删除前需先处理子级结构");
        }
        if (directArticleCount > 0) {
            warnings.add("当前栏目已关联内容，删除前需先调整内容归属");
        }
        if (relatedArticleCount > 0) {
            warnings.add("栏目树调整会影响已归属内容的访问路径和聚合展示");
        }
        response.setWarnings(warnings);
        return response;
    }

    @Transactional
    public void deleteCategory(Long id, Long siteId) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        Category category = getCategoryById(id, siteId);
        if (categoryRepository.countByParentId(category.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前栏目存在子栏目，无法删除");
        }
        if (articleRepository.countByPrimaryCategoryId(category.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前栏目已被内容引用，无法删除");
        }
        categoryRepository.delete(category);
    }

    private Category normalizeRequest(CategoryRequest request, Category existing, Collection<Category> siteCategories) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "栏目数据不能为空");
        }

        Long siteId = request.getSiteId() != null ? request.getSiteId() : existing == null ? null : existing.getSiteId();
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空");
        }
        ensureSiteExists(siteId);

        String name = normalizeName(request.getName(), existing == null ? null : existing.getName());
        String code = normalizeKey(request.getCode(), existing == null ? null : existing.getCode(), "栏目编码不能为空", "栏目编码格式不正确");
        String slug = normalizeKey(request.getSlug(), existing == null ? null : existing.getSlug(), "栏目路径不能为空", "栏目路径格式不正确");
        String type = normalizeEnum(request.getType(), existing == null ? null : existing.getType(), ALLOWED_TYPES, "栏目类型不合法", "channel");
        String status = normalizeStatus(request.getStatus() != null ? request.getStatus() : existing == null ? null : existing.getStatus(), false);
        String aggregationMode = normalizeEnum(request.getAggregationMode(), existing == null ? null : existing.getAggregationMode(), ALLOWED_AGGREGATION, "内容聚合方式不合法", "manual");
        Integer sortOrder = normalizeSortOrder(request.getSortOrder() != null ? request.getSortOrder() : existing == null ? null : existing.getSortOrder());
        Boolean navVisible = request.getNavVisible() != null ? request.getNavVisible() : existing == null ? Boolean.TRUE : existing.getNavVisible();
        Boolean breadcrumbVisible = request.getBreadcrumbVisible() != null ? request.getBreadcrumbVisible() : existing == null ? Boolean.TRUE : existing.getBreadcrumbVisible();
        Boolean publicVisible = request.getPublicVisible() != null ? request.getPublicVisible() : existing == null ? Boolean.TRUE : existing.getPublicVisible();
        Long parentId = request.getParentId() != null || existing == null ? request.getParentId() : existing.getParentId();

        List<Category> allCategories = siteCategories == null
                ? categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(siteId)
                : new ArrayList<>(siteCategories);
        Category parent = null;
        if (parentId != null) {
            parent = allCategories.stream().filter(item -> Objects.equals(item.getId(), parentId)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "父栏目不存在"));
            if (!Objects.equals(parent.getSiteId(), siteId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父栏目不属于当前站点");
            }
            if (existing != null && Objects.equals(existing.getId(), parentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目不能将自己设置为父栏目");
            }
            if (existing != null && isDescendant(existing.getId(), parentId, allCategories.stream().collect(Collectors.toMap(Category::getId, item -> item)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目不能移动到自己的子栏目下");
            }
        }

        int level = parent == null ? 1 : parent.getLevel() + 1;
        if (level > MAX_LEVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "栏目层级超过限制");
        }
        String fullPath = buildFullPath(parent == null ? null : parent.getFullPath(), slug);

        Category normalized = new Category();
        normalized.setSiteId(siteId);
        normalized.setParentId(parentId);
        normalized.setName(name);
        normalized.setCode(code);
        normalized.setType(type);
        normalized.setSlug(slug);
        normalized.setFullPath(fullPath);
        normalized.setLevel(level);
        normalized.setSortOrder(sortOrder);
        normalized.setStatus(status);
        normalized.setNavVisible(navVisible);
        normalized.setBreadcrumbVisible(breadcrumbVisible);
        normalized.setPublicVisible(publicVisible);
        normalized.setListTemplateId(request.getListTemplateId() != null ? request.getListTemplateId() : existing == null ? null : existing.getListTemplateId());
        normalized.setDetailTemplateId(request.getDetailTemplateId() != null ? request.getDetailTemplateId() : existing == null ? null : existing.getDetailTemplateId());
        normalized.setAggregationMode(aggregationMode);
        normalized.setDescription(normalizeOptional(request.getDescription(), existing == null ? null : existing.getDescription()));
        normalized.setSeoTitle(normalizeOptional(request.getSeoTitle(), existing == null ? null : existing.getSeoTitle()));
        normalized.setSeoKeywords(normalizeOptional(request.getSeoKeywords(), existing == null ? null : existing.getSeoKeywords()));
        normalized.setSeoDescription(normalizeOptional(request.getSeoDescription(), existing == null ? null : existing.getSeoDescription()));
        return normalized;
    }

    private void applyMutableFields(Category target, Category source) {
        target.setSiteId(source.getSiteId());
        target.setParentId(source.getParentId());
        target.setName(source.getName());
        target.setCode(source.getCode());
        target.setType(source.getType());
        target.setSlug(source.getSlug());
        target.setFullPath(source.getFullPath());
        target.setLevel(source.getLevel());
        target.setSortOrder(source.getSortOrder());
        target.setStatus(source.getStatus());
        target.setNavVisible(source.getNavVisible());
        target.setBreadcrumbVisible(source.getBreadcrumbVisible());
        target.setPublicVisible(source.getPublicVisible());
        target.setListTemplateId(source.getListTemplateId());
        target.setDetailTemplateId(source.getDetailTemplateId());
        target.setAggregationMode(source.getAggregationMode());
        target.setDescription(source.getDescription());
        target.setSeoTitle(source.getSeoTitle());
        target.setSeoKeywords(source.getSeoKeywords());
        target.setSeoDescription(source.getSeoDescription());
    }

    private void refreshDescendants(Long siteId, Long categoryId) {
        List<Category> siteCategories = categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(siteId);
        Map<Long, Category> categoryMap = siteCategories.stream().collect(Collectors.toMap(Category::getId, item -> item));
        Category root = categoryMap.get(categoryId);
        if (root == null) {
            return;
        }
        refreshChildren(root, siteCategories);
    }

    private void refreshChildren(Category parent, List<Category> siteCategories) {
        List<Category> children = siteCategories.stream()
                .filter(item -> Objects.equals(item.getParentId(), parent.getId()))
                .sorted(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getId))
                .collect(Collectors.toList());
        for (Category child : children) {
            child.setLevel(parent.getLevel() + 1);
            child.setFullPath(buildFullPath(parent.getFullPath(), child.getSlug()));
            categoryRepository.save(child);
            refreshChildren(child, siteCategories);
        }
    }

    private boolean isDescendant(Long categoryId, Long targetParentId, Map<Long, Category> categoryMap) {
        Long current = targetParentId;
        while (current != null) {
            if (Objects.equals(current, categoryId)) {
                return true;
            }
            Category category = categoryMap.get(current);
            if (category == null) {
                return false;
            }
            current = category.getParentId();
        }
        return false;
    }

    private int calculateMaxDepth(Long categoryId, List<Category> siteCategories, int currentDepth) {
        List<Category> children = siteCategories.stream()
                .filter(item -> Objects.equals(item.getParentId(), categoryId))
                .collect(Collectors.toList());
        if (children.isEmpty()) {
            return currentDepth + 1;
        }
        int max = currentDepth + 1;
        for (Category child : children) {
            max = Math.max(max, calculateMaxDepth(child.getId(), siteCategories, currentDepth + 1));
        }
        return max;
    }

    private List<Category> collectSubtree(Long categoryId, List<Category> siteCategories) {
        Map<Long, List<Category>> childrenMap = siteCategories.stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Category> categoryMap = siteCategories.stream().collect(Collectors.toMap(Category::getId, item -> item));
        List<Category> result = new ArrayList<>();
        Category root = categoryMap.get(categoryId);
        if (root == null) {
            return result;
        }
        collectRecursively(root, childrenMap, result);
        return result;
    }

    private void collectRecursively(Category current, Map<Long, List<Category>> childrenMap, List<Category> result) {
        result.add(current);
        List<Category> children = childrenMap.getOrDefault(current.getId(), List.of()).stream()
                .sorted(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getId))
                .collect(Collectors.toList());
        for (Category child : children) {
            collectRecursively(child, childrenMap, result);
        }
    }

    private List<CategoryTreeNode> buildTree(List<Category> categories) {
        Map<Long, CategoryTreeNode> nodeMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, CategoryTreeNode::from, (left, right) -> left, LinkedHashMap::new));
        List<CategoryTreeNode> roots = new ArrayList<>();
        for (Category category : categories) {
            CategoryTreeNode node = nodeMap.get(category.getId());
            if (category.getParentId() == null) {
                roots.add(node);
                continue;
            }
            CategoryTreeNode parent = nodeMap.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private void ensureSiteExists(Long siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不存在");
        }
    }

    private String normalizeName(String value, String fallback) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            normalized = normalizeText(fallback);
        }
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "栏目名称不能为空");
        }
        return normalized;
    }

    private String normalizeKey(String value, String fallback, String emptyMessage, String invalidMessage) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            normalized = normalizeText(fallback);
        }
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, emptyMessage);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidMessage);
        }
        return normalized;
    }

    private String normalizeEnum(String value, String fallback, Set<String> allowedValues, String message, String defaultValue) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            normalized = normalizeText(fallback);
        }
        if (normalized == null) {
            normalized = defaultValue;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeStatus(String status, boolean allowNull) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            return allowNull ? null : "enabled";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "栏目状态不合法");
        }
        return normalized;
    }

    private Integer normalizeSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String normalizeOptional(String value, String fallback) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return normalizeText(fallback);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildFullPath(String parentPath, String slug) {
        if (parentPath == null || parentPath.isBlank() || "/".equals(parentPath)) {
            return "/" + slug;
        }
        return parentPath + "/" + slug;
    }
}
