package gov.cms.admin.dto;

import java.util.List;
import java.util.Map;

public class TemplatePreviewResponse {

    private Long templateId;
    private String templateName;
    private Long templateVersionId;
    private String templateType;
    private Integer versionNo;
    private String pageType;
    private String sourceType;
    private Long sourceId;
    private String layoutSchema;
    private String blockSchema;
    private String seoSchema;
    private String styleSchema;
    private Map<String, Object> context;
    private Map<String, Object> summary;
    private List<String> warnings;
    private String renderedHtml;
    private String renderEngine;
    private String renderTemplateName;
    private String message;

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
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

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getRenderedHtml() {
        return renderedHtml;
    }

    public void setRenderedHtml(String renderedHtml) {
        this.renderedHtml = renderedHtml;
    }

    public String getRenderEngine() {
        return renderEngine;
    }

    public void setRenderEngine(String renderEngine) {
        this.renderEngine = renderEngine;
    }

    public String getRenderTemplateName() {
        return renderTemplateName;
    }

    public void setRenderTemplateName(String renderTemplateName) {
        this.renderTemplateName = renderTemplateName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
