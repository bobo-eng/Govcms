package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class PublishImpactResponse {

    private Long siteId;
    private String unitType;
    private String mode;
    private int totalItems;
    private List<String> warnings = new ArrayList<>();
    private List<Item> items = new ArrayList<>();

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        private String pageType;
        private String sourceType;
        private Long sourceId;
        private String objectType;
        private Long objectId;
        private String path;
        private String action;
        private String summary;

        public String getPageType() { return pageType; }
        public void setPageType(String pageType) { this.pageType = pageType; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public Long getSourceId() { return sourceId; }
        public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public Long getObjectId() { return objectId; }
        public void setObjectId(Long objectId) { this.objectId = objectId; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}