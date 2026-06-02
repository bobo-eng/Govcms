package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class MediaReferenceSummary {

    private Long mediaId;
    private String originalName;
    private boolean filePresent;
    private int contentReferenceCount;
    private int topicReferenceCount;
    private List<String> contentReferences = new ArrayList<>();
    private List<String> topicReferences = new ArrayList<>();

    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public boolean isFilePresent() { return filePresent; }
    public void setFilePresent(boolean filePresent) { this.filePresent = filePresent; }
    public int getContentReferenceCount() { return contentReferenceCount; }
    public void setContentReferenceCount(int contentReferenceCount) { this.contentReferenceCount = contentReferenceCount; }
    public int getTopicReferenceCount() { return topicReferenceCount; }
    public void setTopicReferenceCount(int topicReferenceCount) { this.topicReferenceCount = topicReferenceCount; }
    public List<String> getContentReferences() { return contentReferences; }
    public void setContentReferences(List<String> contentReferences) { this.contentReferences = contentReferences; }
    public List<String> getTopicReferences() { return topicReferences; }
    public void setTopicReferences(List<String> topicReferences) { this.topicReferences = topicReferences; }
}
