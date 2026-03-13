package gov.cms.admin.dto;

public class TemplateRequest {

    private Long siteId;
    private String name;
    private String code;
    private String type;
    private String status;
    private String description;
    private String layoutSchema;
    private String blockSchema;
    private String seoSchema;
    private String styleSchema;
    private String changeLog;
    private String defaultPreviewSource;

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLayoutSchema() {
        return layoutSchema;
    }

    public void setLayoutSchema(String layoutSchema) {
        this.layoutSchema = layoutSchema;
    }

    public String getBlockSchema() {
        return blockSchema;
    }

    public void setBlockSchema(String blockSchema) {
        this.blockSchema = blockSchema;
    }

    public String getSeoSchema() {
        return seoSchema;
    }

    public void setSeoSchema(String seoSchema) {
        this.seoSchema = seoSchema;
    }

    public String getStyleSchema() {
        return styleSchema;
    }

    public void setStyleSchema(String styleSchema) {
        this.styleSchema = styleSchema;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public String getDefaultPreviewSource() {
        return defaultPreviewSource;
    }

    public void setDefaultPreviewSource(String defaultPreviewSource) {
        this.defaultPreviewSource = defaultPreviewSource;
    }
}
