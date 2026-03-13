package gov.cms.admin.service;

import gov.cms.admin.dto.PortalRenderResult;
import gov.cms.admin.dto.RenderContextSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PortalRenderService {

    private static final String RENDER_ENGINE = "thymeleaf";
    private final SpringTemplateEngine templateEngine;

    public PortalRenderService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public PortalRenderResult render(RenderContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Render snapshot is required.");
        }
        String templateName = resolvePageTemplate(snapshot.getPageType());
        Map<String, String> slotHtml = renderSlots(snapshot);

        Context context = new Context(Locale.SIMPLIFIED_CHINESE);
        context.setVariable("snapshot", snapshot);
        context.setVariable("pageType", snapshot.getPageType());
        context.setVariable("pageTitle", resolvePageTitle(snapshot));
        context.setVariable("layoutSlots", snapshot.getLayoutSlots());
        context.setVariable("slotHtml", slotHtml);
        context.setVariable("warnings", snapshot.getWarnings());
        context.setVariable("renderMeta", valueFromContext(snapshot, "renderMeta"));
        context.setVariable("siteContext", valueFromContext(snapshot, "siteContext"));
        context.setVariable("navigationContext", valueFromContext(snapshot, "navigationContext"));
        context.setVariable("columnContext", valueFromContext(snapshot, "columnContext"));
        context.setVariable("contentContext", valueFromContext(snapshot, "contentContext"));
        context.setVariable("topicContext", valueFromContext(snapshot, "topicContext"));

        String html = templateEngine.process(templateName, context);

        PortalRenderResult result = new PortalRenderResult();
        result.setRenderedHtml(html);
        result.setRenderEngine(RENDER_ENGINE);
        result.setRenderTemplateName(templateName);
        result.setWarnings(snapshot.getWarnings());
        result.setPageType(snapshot.getPageType());
        result.setPathHint(snapshot.getPathHint());
        return result;
    }

    private Map<String, String> renderSlots(RenderContextSnapshot snapshot) {
        Map<String, StringBuilder> buffers = new LinkedHashMap<>();
        for (RenderContextSnapshot.LayoutSlot slot : snapshot.getLayoutSlots()) {
            buffers.put(slot.getName(), new StringBuilder());
        }
        if (!buffers.containsKey("main")) {
            buffers.put("main", new StringBuilder());
        }
        for (RenderContextSnapshot.RenderBlock block : snapshot.getRenderBlocks()) {
            String slot = block.getSlot() == null ? "main" : block.getSlot();
            StringBuilder buffer = buffers.computeIfAbsent(slot, key -> new StringBuilder());
            buffer.append(renderBlock(snapshot, block));
        }
        Map<String, String> slotHtml = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : buffers.entrySet()) {
            slotHtml.put(entry.getKey(), entry.getValue().toString());
        }
        return slotHtml;
    }

    private String renderBlock(RenderContextSnapshot snapshot, RenderContextSnapshot.RenderBlock block) {
        Context context = new Context(Locale.SIMPLIFIED_CHINESE);
        context.setVariable("snapshot", snapshot);
        context.setVariable("block", block);
        context.setVariable("props", block.getProps());
        context.setVariable("data", block.getData());
        context.setVariable("renderMeta", valueFromContext(snapshot, "renderMeta"));
        context.setVariable("siteContext", valueFromContext(snapshot, "siteContext"));
        context.setVariable("navigationContext", valueFromContext(snapshot, "navigationContext"));
        context.setVariable("columnContext", valueFromContext(snapshot, "columnContext"));
        context.setVariable("contentContext", valueFromContext(snapshot, "contentContext"));
        return templateEngine.process("portal/block", context);
    }

    private Object valueFromContext(RenderContextSnapshot snapshot, String key) {
        return snapshot.getContext() == null ? null : snapshot.getContext().get(key);
    }

    private String resolvePageTemplate(String pageType) {
        return switch (pageType) {
            case "home" -> "portal/page/home";
            case "column-list" -> "portal/page/column-list";
            case "content-detail" -> "portal/page/content-detail";
            case "error-404" -> "portal/page/error-404";
            case "topic-page" -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic page rendering is not supported yet.");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported pageType for rendering.");
        };
    }

    private String resolvePageTitle(RenderContextSnapshot snapshot) {
        Map<String, Object> siteContext = castMap(valueFromContext(snapshot, "siteContext"));
        Map<String, Object> columnContext = castMap(valueFromContext(snapshot, "columnContext"));
        Map<String, Object> contentContext = castMap(valueFromContext(snapshot, "contentContext"));
        if ("content-detail".equals(snapshot.getPageType()) && contentContext != null && contentContext.get("title") != null) {
            return String.valueOf(contentContext.get("title"));
        }
        if ("column-list".equals(snapshot.getPageType()) && columnContext != null && columnContext.get("name") != null) {
            return String.valueOf(columnContext.get("name"));
        }
        if ("error-404".equals(snapshot.getPageType())) {
            return "404 - 页面不存在";
        }
        if (siteContext != null && siteContext.get("name") != null) {
            return String.valueOf(siteContext.get("name"));
        }
        return "GovCMS Portal";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
