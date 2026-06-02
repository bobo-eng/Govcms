package gov.cms.admin.service;

import gov.cms.admin.dto.TopicContentItemsRequest;
import gov.cms.admin.dto.TopicRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.entity.TopicContentItem;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TopicContentItemRepository;
import gov.cms.admin.repository.TopicRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final ArticleRepository articleRepository;
    private final TemplateRepository templateRepository;
    private final CategoryRepository categoryRepository;
    private final SiteAccessService siteAccessService;

    public TopicService(TopicRepository topicRepository,
                        TopicContentItemRepository topicContentItemRepository,
                        ArticleRepository articleRepository,
                        TemplateRepository templateRepository,
                        CategoryRepository categoryRepository,
                        SiteAccessService siteAccessService) {
        this.topicRepository = topicRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.articleRepository = articleRepository;
        this.templateRepository = templateRepository;
        this.categoryRepository = categoryRepository;
        this.siteAccessService = siteAccessService;
    }

    public List<Topic> getTopics(Long siteId, String keyword, String status) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(siteId);
        return topicRepository.search(accessibleSiteId, keyword, normalize(status));
    }

    public Topic getTopicById(Long id, Long siteId) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(siteId);
        return topicRepository.findByIdAndSiteId(id, accessibleSiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found."));
    }

    @Transactional
    public Topic createTopic(TopicRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic payload is required.");
        }
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(request.getSiteId());
        Topic topic = new Topic();
        applyPayload(topic, request, accessibleSiteId, null);
        return topicRepository.save(topic);
    }

    @Transactional
    public Topic updateTopic(Long id, TopicRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic payload is required.");
        }
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(request.getSiteId());
        Topic topic = topicRepository.findByIdAndSiteId(id, accessibleSiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found."));
        applyPayload(topic, request, accessibleSiteId, id);
        return topicRepository.save(topic);
    }

    @Transactional
    public void deleteTopic(Long id, Long siteId) {
        Topic topic = getTopicById(id, siteId);
        topicContentItemRepository.deleteByTopicId(topic.getId());
        topicRepository.delete(topic);
    }

    public List<TopicContentItem> getTopicContentItems(Long id, Long siteId) {
        Topic topic = getTopicById(id, siteId);
        return topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(topic.getId());
    }

    @Transactional
    public List<TopicContentItem> replaceTopicContentItems(Long id, TopicContentItemsRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required.");
        }
        Topic topic = getTopicById(id, request.getSiteId());
        topicContentItemRepository.deleteByTopicId(topic.getId());
        int index = 0;
        for (Long articleId : request.getArticleIds()) {
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article not found."));
            if (!topic.getSiteId().equals(article.getSiteId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article does not belong to topic site.");
            }
            TopicContentItem item = new TopicContentItem();
            item.setTopicId(topic.getId());
            item.setArticleId(articleId);
            item.setSortOrder(index++);
            topicContentItemRepository.save(item);
        }
        return topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(topic.getId());
    }

    public List<Article> resolvePublishedArticles(Topic topic) {
        if (topic == null) {
            return List.of();
        }
        int limit = normalizeRuleLimit(topic.getRuleLimit());
        if ("rule_based".equalsIgnoreCase(topic.getAggregationMode())) {
            if (topic.getRuleCategoryId() != null) {
                return articleRepository.findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(
                        topic.getSiteId(),
                        topic.getRuleCategoryId(),
                        ArticleStatus.published,
                        PageRequest.of(0, limit)
                );
            }
            return articleRepository.searchArticles(null, null, ArticleStatus.published, topic.getSiteId(), null, PageRequest.of(0, limit)).getContent();
        }

        List<TopicContentItem> items = topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(topic.getId());
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> order = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (TopicContentItem item : items) {
            ids.add(item.getArticleId());
            order.put(item.getArticleId(), item.getSortOrder() == null ? Integer.MAX_VALUE : item.getSortOrder());
        }
        return articleRepository.findAllById(ids).stream()
                .filter(article -> article.getStatus() == ArticleStatus.published)
                .filter(article -> topic.getSiteId().equals(article.getSiteId()))
                .sorted((left, right) -> Integer.compare(order.getOrDefault(left.getId(), Integer.MAX_VALUE), order.getOrDefault(right.getId(), Integer.MAX_VALUE)))
                .limit(limit)
                .toList();
    }

    public int countPublishableArticles(Topic topic) {
        return resolvePublishedArticles(topic).size();
    }

    private void applyPayload(Topic topic, TopicRequest request, Long siteId, Long existingId) {
        String name = required(request.getName(), "Topic name is required.");
        String code = required(request.getCode(), "Topic code is required.").toLowerCase(Locale.ROOT);
        String slug = required(request.getSlug(), "Topic slug is required.").toLowerCase(Locale.ROOT);
        String aggregationMode = normalize(request.getAggregationMode()) == null ? "manual" : normalize(request.getAggregationMode());
        if (!Set.of("manual", "rule_based").contains(aggregationMode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported topic aggregation mode.");
        }
        if (topicRepository.existsBySiteIdAndCodeIgnoreCase(siteId, code) && existingId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Topic code already exists.");
        }
        if (existingId != null && topicRepository.existsBySiteIdAndCodeIgnoreCaseAndIdNot(siteId, code, existingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Topic code already exists.");
        }
        if (topicRepository.existsBySiteIdAndSlugIgnoreCase(siteId, slug) && existingId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Topic slug already exists.");
        }
        if (existingId != null && topicRepository.existsBySiteIdAndSlugIgnoreCaseAndIdNot(siteId, slug, existingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Topic slug already exists.");
        }
        if (request.getTemplateId() != null && templateRepository.findByIdAndSiteId(request.getTemplateId(), siteId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template not found in site.");
        }
        if (request.getRuleCategoryId() != null && categoryRepository.findByIdAndSiteId(request.getRuleCategoryId(), siteId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule category not found in site.");
        }
        topic.setSiteId(siteId);
        topic.setName(name);
        topic.setCode(code);
        topic.setSlug(slug);
        topic.setSummary(normalize(request.getSummary()));
        topic.setStatus(normalize(request.getStatus()) == null ? "draft" : normalize(request.getStatus()));
        topic.setTemplateId(request.getTemplateId());
        topic.setAggregationMode(aggregationMode);
        topic.setRuleCategoryId("rule_based".equals(aggregationMode) ? request.getRuleCategoryId() : null);
        topic.setRuleLimit(normalizeRuleLimit(request.getRuleLimit()));
        topic.setSeoTitle(normalize(request.getSeoTitle()));
        topic.setSeoKeywords(normalize(request.getSeoKeywords()));
        topic.setSeoDescription(normalize(request.getSeoDescription()));
        topic.setNavVisible(request.getNavVisible() == null ? Boolean.FALSE : request.getNavVisible());
    }

    private int normalizeRuleLimit(Integer value) {
        if (value == null) {
            return 10;
        }
        return Math.min(50, Math.max(1, value));
    }

    private String required(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
