package gov.cms.admin.dto;

public class TemplateBindingRequest {

    private Long siteId;
    private String targetType;
    private Long targetId;
    private String bindingSlot;
    private Long templateVersionId;
    private Boolean replaceExisting;

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getBindingSlot() {
        return bindingSlot;
    }

    public void setBindingSlot(String bindingSlot) {
        this.bindingSlot = bindingSlot;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public Boolean getReplaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(Boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }
}
