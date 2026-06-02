package gov.cms.admin.dto;

public class SearchSuggestionItem {
    private String keyword;
    private String source;
    private Long count;

    public SearchSuggestionItem() {
    }

    public SearchSuggestionItem(String keyword, String source, Long count) {
        this.keyword = keyword;
        this.source = source;
        this.count = count;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }
}
