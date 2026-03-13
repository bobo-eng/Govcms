package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RenderContextSnapshot {

    private Long templateId;
    private String templateName;
    private Long templateVersionId;
    private String templateType;
    private Integer versionNo;
    private String pageType;
    private String sourceType;
    private Long sourceId;
    private String pathHint;
    private String layoutSchema;
    private String blockSchema;
    private String seoSchema;
    private String styleSchema;
    private Map<String, Object> context = new LinkedHashMap<>();
    private Map<String, Object> summary = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();
    private boolean publishReady;
    private List<LayoutSlot> layoutSlots = new ArrayList<>();
    private List<RenderBlock> renderBlocks = new ArrayList<>();

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

    public String getPathHint() {
        return pathHint;
    }

    public void setPathHint(String pathHint) {
        this.pathHint = pathHint;
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

    public boolean isPublishReady() {
        return publishReady;
    }

    public void setPublishReady(boolean publishReady) {
        this.publishReady = publishReady;
    }

    public List<LayoutSlot> getLayoutSlots() {
        return layoutSlots;
    }

    public void setLayoutSlots(List<LayoutSlot> layoutSlots) {
        this.layoutSlots = layoutSlots;
    }

    public List<RenderBlock> getRenderBlocks() {
        return renderBlocks;
    }

    public void setRenderBlocks(List<RenderBlock> renderBlocks) {
        this.renderBlocks = renderBlocks;
    }

    public static class LayoutSlot {
        private String name;
        private String label;
        private Map<String, Object> props = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Map<String, Object> getProps() {
            return props;
        }

        public void setProps(Map<String, Object> props) {
            this.props = props;
        }
    }

    public static class RenderBlock {
        private String id;
        private String type;
        private String slot;
        private Map<String, Object> props = new LinkedHashMap<>();
        private Object data;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSlot() {
            return slot;
        }

        public void setSlot(String slot) {
            this.slot = slot;
        }

        public Map<String, Object> getProps() {
            return props;
        }

        public void setProps(Map<String, Object> props) {
            this.props = props;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}
