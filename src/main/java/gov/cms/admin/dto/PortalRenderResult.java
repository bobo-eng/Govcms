package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class PortalRenderResult {

    private String renderedHtml;
    private String renderEngine;
    private String renderTemplateName;
    private List<String> warnings = new ArrayList<>();
    private String pageType;
    private String pathHint;

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

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public String getPathHint() {
        return pathHint;
    }

    public void setPathHint(String pathHint) {
        this.pathHint = pathHint;
    }
}
