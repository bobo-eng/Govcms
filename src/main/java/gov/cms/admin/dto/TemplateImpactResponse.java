package gov.cms.admin.dto;

import java.util.List;
import java.util.Map;

public class TemplateImpactResponse {

    private Long templateId;
    private String templateName;
    private String templateType;
    private long activeBindingCount;
    private Map<String, Long> targetTypeCounts;
    private List<String> sampleTargets;
    private List<String> warnings;

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

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    public long getActiveBindingCount() {
        return activeBindingCount;
    }

    public void setActiveBindingCount(long activeBindingCount) {
        this.activeBindingCount = activeBindingCount;
    }

    public Map<String, Long> getTargetTypeCounts() {
        return targetTypeCounts;
    }

    public void setTargetTypeCounts(Map<String, Long> targetTypeCounts) {
        this.targetTypeCounts = targetTypeCounts;
    }

    public List<String> getSampleTargets() {
        return sampleTargets;
    }

    public void setSampleTargets(List<String> sampleTargets) {
        this.sampleTargets = sampleTargets;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
