package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class TopicContentItemsRequest {
    private Long siteId;
    private List<Long> articleIds = new ArrayList<>();

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public List<Long> getArticleIds() { return articleIds; }
    public void setArticleIds(List<Long> articleIds) { this.articleIds = articleIds == null ? new ArrayList<>() : new ArrayList<>(articleIds); }
}
