package gov.cms.admin.service;

import gov.cms.admin.dto.PortalRenderResult;
import gov.cms.admin.dto.RenderContextSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalRenderServiceTest {

    private PortalRenderService portalRenderService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        portalRenderService = new PortalRenderService(templateEngine);
    }

    @Test
    void renderContentDetailProducesHtml() {
        RenderContextSnapshot snapshot = new RenderContextSnapshot();
        snapshot.setPageType("content-detail");
        snapshot.setPathHint("/news/99.html");
        snapshot.setWarnings(List.of("Content is not published; preview stays in controlled mode only."));

        RenderContextSnapshot.LayoutSlot slot = new RenderContextSnapshot.LayoutSlot();
        slot.setName("main");
        slot.setLabel("main");
        snapshot.setLayoutSlots(List.of(slot));

        RenderContextSnapshot.RenderBlock header = new RenderContextSnapshot.RenderBlock();
        header.setType("content_header");
        header.setSlot("main");
        header.setData(Map.of("title", "Article Title", "summary", "Summary"));

        RenderContextSnapshot.RenderBlock body = new RenderContextSnapshot.RenderBlock();
        body.setType("content_body");
        body.setSlot("main");
        body.setData(Map.of("html", "<p>Body</p>"));

        snapshot.setRenderBlocks(List.of(header, body));
        snapshot.setContext(new LinkedHashMap<>(Map.of(
                "siteContext", Map.of("name", "Gov Main"),
                "contentContext", Map.of("title", "Article Title"),
                "renderMeta", Map.of("publishReady", false)
        )));

        PortalRenderResult result = portalRenderService.render(snapshot);

        assertEquals("thymeleaf", result.getRenderEngine());
        assertEquals("portal/page/content-detail", result.getRenderTemplateName());
        assertTrue(result.getRenderedHtml().contains("Article Title"));
        assertTrue(result.getRenderedHtml().contains("<p>Body</p>"));
    }

    @Test
    void renderRejectsTopicPageForNow() {
        RenderContextSnapshot snapshot = new RenderContextSnapshot();
        snapshot.setPageType("topic-page");
        snapshot.setLayoutSlots(List.of());
        snapshot.setRenderBlocks(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> portalRenderService.render(snapshot));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
