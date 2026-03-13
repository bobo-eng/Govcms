package gov.cms.admin.dto;

public class TemplateVersionRequest {

    private String layoutSchema;
    private String blockSchema;
    private String seoSchema;
    private String styleSchema;
    private String changeLog;

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
}
