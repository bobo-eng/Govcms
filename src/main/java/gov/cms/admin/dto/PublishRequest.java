package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class PublishRequest {

    private Long siteId;
    private String unitType;
    private List<Long> unitIds = new ArrayList<>();
    private String mode;
    private String operatorComment;

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

    public List<Long> getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(List<Long> unitIds) {
        this.unitIds = unitIds == null ? new ArrayList<>() : new ArrayList<>(unitIds);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOperatorComment() {
        return operatorComment;
    }

    public void setOperatorComment(String operatorComment) {
        this.operatorComment = operatorComment;
    }
}