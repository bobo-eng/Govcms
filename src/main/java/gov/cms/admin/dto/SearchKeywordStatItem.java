package gov.cms.admin.dto;

public class SearchKeywordStatItem {
    private String keyword;
    private long count;

    public SearchKeywordStatItem() {
    }

    public SearchKeywordStatItem(String keyword, long count) {
        this.keyword = keyword;
        this.count = count;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
