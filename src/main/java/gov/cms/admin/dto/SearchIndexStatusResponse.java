package gov.cms.admin.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SearchIndexStatusResponse {
    private Long siteId;
    private long totalEntries;
    private LocalDateTime lastRebuildAt;
    private String lastRebuildSummary;
    private String lastFailureReason;
    private List<SearchKeywordStatItem> hotKeywords = new ArrayList<>();
    private List<SearchKeywordStatItem> zeroResultKeywords = new ArrayList<>();
    private List<SearchKeywordStatItem> lowResultKeywords = new ArrayList<>();

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public long getTotalEntries() { return totalEntries; }
    public void setTotalEntries(long totalEntries) { this.totalEntries = totalEntries; }
    public LocalDateTime getLastRebuildAt() { return lastRebuildAt; }
    public void setLastRebuildAt(LocalDateTime lastRebuildAt) { this.lastRebuildAt = lastRebuildAt; }
    public String getLastRebuildSummary() { return lastRebuildSummary; }
    public void setLastRebuildSummary(String lastRebuildSummary) { this.lastRebuildSummary = lastRebuildSummary; }
    public String getLastFailureReason() { return lastFailureReason; }
    public void setLastFailureReason(String lastFailureReason) { this.lastFailureReason = lastFailureReason; }
    public List<SearchKeywordStatItem> getHotKeywords() { return hotKeywords; }
    public void setHotKeywords(List<SearchKeywordStatItem> hotKeywords) { this.hotKeywords = hotKeywords; }
    public List<SearchKeywordStatItem> getZeroResultKeywords() { return zeroResultKeywords; }
    public void setZeroResultKeywords(List<SearchKeywordStatItem> zeroResultKeywords) { this.zeroResultKeywords = zeroResultKeywords; }
    public List<SearchKeywordStatItem> getLowResultKeywords() { return lowResultKeywords; }
    public void setLowResultKeywords(List<SearchKeywordStatItem> lowResultKeywords) { this.lowResultKeywords = lowResultKeywords; }
}
