package gov.cms.admin.dto;

public class CategoryMoveRequest {

    private Long siteId;
    private Long targetParentId;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getTargetParentId() { return targetParentId; }
    public void setTargetParentId(Long targetParentId) { this.targetParentId = targetParentId; }
}
