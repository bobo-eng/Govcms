package gov.cms.admin.service;

import gov.cms.admin.dto.MediaReferenceSummary;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.MediaFile;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.MediaFileRepository;
import gov.cms.admin.repository.TopicContentItemRepository;
import gov.cms.admin.repository.TopicRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MediaReferenceService {

    private static final Pattern MEDIA_ID_PATTERN = Pattern.compile("/(?:api/)?media/(\\d+)/preview", Pattern.CASE_INSENSITIVE);

    private final MediaFileRepository mediaFileRepository;
    private final ArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final MediaStorageService mediaStorageService;

    public MediaReferenceService(MediaFileRepository mediaFileRepository,
                                 ArticleRepository articleRepository,
                                 TopicRepository topicRepository,
                                 TopicContentItemRepository topicContentItemRepository,
                                 MediaStorageService mediaStorageService) {
        this.mediaFileRepository = mediaFileRepository;
        this.articleRepository = articleRepository;
        this.topicRepository = topicRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.mediaStorageService = mediaStorageService;
    }

    public MediaReferenceSummary summarize(Long mediaId) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaId).orElseThrow();
        MediaReferenceSummary summary = new MediaReferenceSummary();
        summary.setMediaId(mediaFile.getId());
        summary.setOriginalName(mediaFile.getOriginalName());
        summary.setFilePresent(mediaStorageService.exists(mediaFile.getStoragePath()));

        List<String> contentRefs = new ArrayList<>();
        for (Article article : articleRepository.findAll()) {
            if (referencesMedia(article.getContent(), mediaFile) || referencesMedia(article.getSummary(), mediaFile)) {
                contentRefs.add(article.getTitle() + " (#" + article.getId() + ")");
            }
        }
        summary.setContentReferences(contentRefs);
        summary.setContentReferenceCount(contentRefs.size());

        List<String> topicRefs = new ArrayList<>();
        for (Topic topic : topicRepository.findAll()) {
            boolean referenced = referencesMedia(topic.getSummary(), mediaFile) || referencesMedia(topic.getSeoDescription(), mediaFile);
            if (!referenced) {
                List<Long> articleIds = topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(topic.getId()).stream().map(item -> item.getArticleId()).toList();
                for (Article article : articleRepository.findAllById(articleIds)) {
                    if (referencesMedia(article.getContent(), mediaFile) || referencesMedia(article.getSummary(), mediaFile)) {
                        referenced = true;
                        break;
                    }
                }
            }
            if (referenced) {
                topicRefs.add(topic.getName() + " (#" + topic.getId() + ")");
            }
        }
        summary.setTopicReferences(topicRefs);
        summary.setTopicReferenceCount(topicRefs.size());
        return summary;
    }

    public List<String> collectMissingMediaWarningsForArticle(Article article) {
        return collectMissingMediaWarnings(List.of(article), List.of());
    }

    public List<String> collectMissingMediaWarningsForTopic(Topic topic) {
        List<Article> articles = articleRepository.findAllById(topicContentItemRepository.findByTopicIdOrderBySortOrderAscIdAsc(topic.getId()).stream().map(item -> item.getArticleId()).toList());
        return collectMissingMediaWarnings(articles, List.of(topic));
    }

    private List<String> collectMissingMediaWarnings(List<Article> articles, List<Topic> topics) {
        Set<String> warnings = new LinkedHashSet<>();
        for (Article article : articles) {
            collectWarningsFromText(article.getContent(), warnings, "内容", article.getId());
            collectWarningsFromText(article.getSummary(), warnings, "内容", article.getId());
        }
        for (Topic topic : topics) {
            collectWarningsFromText(topic.getSummary(), warnings, "专题", topic.getId());
            collectWarningsFromText(topic.getSeoDescription(), warnings, "专题", topic.getId());
        }
        return new ArrayList<>(warnings);
    }

    private void collectWarningsFromText(String text, Set<String> warnings, String ownerType, Long ownerId) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = MEDIA_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            Long mediaId = Long.valueOf(matcher.group(1));
            MediaFile mediaFile = mediaFileRepository.findById(mediaId).orElse(null);
            if (mediaFile == null) {
                warnings.add(ownerType + " #" + ownerId + " 引用了不存在的媒体 #" + mediaId);
                continue;
            }
            if (!mediaStorageService.exists(mediaFile.getStoragePath())) {
                warnings.add(ownerType + " #" + ownerId + " 引用了缺失文件的媒体 #" + mediaId);
            }
        }
    }

    private boolean referencesMedia(String text, MediaFile mediaFile) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains("/api/media/" + mediaFile.getId() + "/preview") || text.contains("/media/" + mediaFile.getId() + "/preview")) {
            return true;
        }
        return Objects.equals(mediaFile.getStoragePath(), null) ? false : text.contains(mediaFile.getStoragePath()) || text.contains(mediaFile.getStoredName());
    }
}
