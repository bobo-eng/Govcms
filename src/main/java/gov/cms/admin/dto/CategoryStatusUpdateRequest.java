package gov.cms.admin.dto;

public class CategoryStatusUpdateRequest {

    private Long siteId;
    private String status;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
