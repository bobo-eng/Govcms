package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class PublishCheckResponse {

    private Long siteId;
    private String unitType;
    private String mode;
    private boolean publishable;
    private int impactCount;
    private List<String> reasons = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

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

    public boolean isPublishable() {
        return publishable;
    }

    public void setPublishable(boolean publishable) {
        this.publishable = publishable;
    }

    public int getImpactCount() {
        return impactCount;
    }

    public void setImpactCount(int impactCount) {
        this.impactCount = impactCount;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}