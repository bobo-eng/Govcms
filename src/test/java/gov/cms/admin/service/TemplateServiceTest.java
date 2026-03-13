package gov.cms.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.PortalRenderResult;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.dto.TemplateBindingRequest;
import gov.cms.admin.dto.TemplatePreviewRequest;
import gov.cms.admin.dto.TemplateRequest;
import gov.cms.admin.dto.TemplateVersionRequest;
import gov.cms.admin.dto.TemplateVersionRollbackRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.entity.TemplateVersion;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateVersionRepository templateVersionRepository;
    @Mock private TemplateBindingRepository templateBindingRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private RenderContextAssembler renderContextAssembler;
    @Mock private PortalRenderService portalRenderService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private TemplateService templateService;

    @Test
    void createTemplateCreatesInitialVersion() {
        TemplateRequest request = buildTemplateRequest();
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(templateRepository.existsBySiteIdAndCodeIgnoreCase(1L, "home-main")).thenReturn(false);
        doAnswer(invocation -> {
            Template template = invocation.getArgument(0);
            if (template.getId() == null) {
                template.setId(10L);
            }
            return template;
        }).when(templateRepository).save(any(Template.class));
        doAnswer(invocation -> {
            TemplateVersion version = invocation.getArgument(0);
            version.setId(100L);
            return version;
        }).when(templateVersionRepository).save(any(TemplateVersion.class));

        Template saved = templateService.createTemplate(request);

        assertEquals(10L, saved.getId());
        assertEquals(100L, saved.getCurrentVersionId());
        assertEquals(1, saved.getLatestVersionNo());
    }

    @Test
    void createTemplateRejectsDuplicateCode() {
        TemplateRequest request = buildTemplateRequest();
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(templateRepository.existsBySiteIdAndCodeIgnoreCase(1L, "home-main")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> templateService.createTemplate(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void createVersionIncrementsVersionNumber() {
        Template template = buildTemplate();
        template.setLatestVersionNo(1);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        doAnswer(invocation -> {
            TemplateVersion version = invocation.getArgument(0);
            version.setId(101L);
            return version;
        }).when(templateVersionRepository).save(any(TemplateVersion.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateVersionRequest request = new TemplateVersionRequest();
        request.setLayoutSchema("{\"layout\":[]}");
        request.setBlockSchema("{\"blocks\":[]}");
        request.setChangeLog("save v2");

        TemplateVersion saved = templateService.createVersion(10L, request);

        assertEquals(2, saved.getVersionNo());
        assertEquals(101L, saved.getId());
    }

    @Test
    void rollbackCreatesNewVersionRecord() {
        Template template = buildTemplate();
        template.setLatestVersionNo(2);
        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdAndVersionNo(10L, 1)).thenReturn(Optional.of(buildVersion(10L, 1)));
        doAnswer(invocation -> {
            TemplateVersion version = invocation.getArgument(0);
            version.setId(103L);
            return version;
        }).when(templateVersionRepository).save(any(TemplateVersion.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateVersionRollbackRequest request = new TemplateVersionRollbackRequest();
        request.setSiteId(1L);
        request.setVersionNo(1);

        Template saved = templateService.rollbackVersion(10L, request);

        assertEquals(3, saved.getLatestVersionNo());
        assertEquals(103L, saved.getCurrentVersionId());
    }

    @Test
    void createBindingRejectsConflictWithoutReplaceExisting() {
        Template template = buildTemplate();
        template.setType("column_list");
        template.setStatus("active");
        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        when(categoryRepository.findByIdAndSiteId(7L, 1L)).thenReturn(Optional.of(buildCategory(7L, 1L)));
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 7L, "column_list", "active"))
                .thenReturn(List.of(new TemplateBinding()));

        TemplateBindingRequest request = new TemplateBindingRequest();
        request.setSiteId(1L);
        request.setTargetType("column");
        request.setTargetId(7L);
        request.setBindingSlot("column_list");
        request.setReplaceExisting(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> templateService.createBinding(10L, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void previewTemplateBuildsRenderContextForContent() {
        RenderContextSnapshot snapshot = new RenderContextSnapshot();
        snapshot.setTemplateId(10L);
        snapshot.setTemplateName("Template");
        snapshot.setTemplateVersionId(102L);
        snapshot.setTemplateType("content_detail");
        snapshot.setVersionNo(2);
        snapshot.setPageType("content-detail");
        snapshot.setSourceType("content");
        snapshot.setSourceId(99L);
        snapshot.setLayoutSchema("{\"layout\":[]}");
        snapshot.setBlockSchema("{\"blocks\":[]}");
        snapshot.setSeoSchema("{\"title\":\"demo\"}");
        snapshot.setStyleSchema("{\"theme\":{}}");
        snapshot.setContext(Map.of(
                "siteContext", Map.of("name", "Gov Main"),
                "contentContext", Map.of("title", "Article")
        ));
        snapshot.setSummary(Map.of("pageType", "content-detail"));
        snapshot.setWarnings(List.of("Content is not published; preview stays in controlled mode only."));

        PortalRenderResult renderResult = new PortalRenderResult();
        renderResult.setRenderedHtml("<html><body>preview</body></html>");
        renderResult.setRenderEngine("thymeleaf");
        renderResult.setRenderTemplateName("portal/page/content-detail");

        when(renderContextAssembler.assemble(any(RenderRequest.class))).thenReturn(snapshot);
        when(portalRenderService.render(snapshot)).thenReturn(renderResult);

        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setSiteId(1L);
        request.setSourceType("content");
        request.setSourceId(99L);

        var response = templateService.previewTemplate(10L, request);

        assertEquals("content", response.getSourceType());
        assertEquals("content-detail", response.getPageType());
        assertTrue(response.getContext().containsKey("siteContext"));
        assertTrue(response.getContext().containsKey("contentContext"));
        assertTrue(response.getWarnings().stream().anyMatch(item -> item.contains("not published") || item.contains("controlled mode")));
        assertEquals("<html><body>preview</body></html>", response.getRenderedHtml());
        assertEquals("thymeleaf", response.getRenderEngine());
        assertEquals("portal/page/content-detail", response.getRenderTemplateName());
    }

    @Test
    void getImpactAggregatesBindingCounts() {
        Template template = buildTemplate();
        when(templateRepository.findByIdAndSiteId(10L, 1L)).thenReturn(Optional.of(template));
        TemplateBinding a = new TemplateBinding();
        a.setTargetType("site");
        a.setTargetId(1L);
        a.setBindingSlot("site_home");
        TemplateBinding b = new TemplateBinding();
        b.setTargetType("column");
        b.setTargetId(7L);
        b.setBindingSlot("column_list");
        when(templateBindingRepository.findByTemplateIdAndStatus(10L, "active")).thenReturn(List.of(a, b));

        var impact = templateService.getImpact(10L, 1L);

        assertEquals(2, impact.getActiveBindingCount());
        assertEquals(1L, impact.getTargetTypeCounts().get("site"));
        assertEquals(1L, impact.getTargetTypeCounts().get("column"));
    }

    private TemplateRequest buildTemplateRequest() {
        TemplateRequest request = new TemplateRequest();
        request.setSiteId(1L);
        request.setName("Home Main");
        request.setCode("home-main");
        request.setType("home");
        request.setStatus("active");
        request.setLayoutSchema("{\"layout\":[]}");
        request.setBlockSchema("{\"blocks\":[]}");
        request.setChangeLog("init");
        request.setDefaultPreviewSource("sample");
        return request;
    }

    private Template buildTemplate() {
        Template template = new Template();
        template.setId(10L);
        template.setSiteId(1L);
        template.setName("Template");
        template.setCode("template");
        template.setType("home");
        template.setStatus("active");
        template.setCurrentVersionId(100L);
        template.setLatestVersionNo(1);
        return template;
    }

    private TemplateVersion buildVersion(Long templateId, Integer versionNo) {
        TemplateVersion version = new TemplateVersion();
        version.setId(100L + versionNo);
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setLayoutSchema("{\"layout\":[]}");
        version.setBlockSchema("{\"blocks\":[]}");
        return version;
    }

    private Category buildCategory(Long id, Long siteId) {
        Category category = new Category();
        category.setId(id);
        category.setSiteId(siteId);
        category.setName("News");
        category.setCode("news");
        category.setSlug("news");
        category.setFullPath("/news");
        category.setLevel(1);
        return category;
    }

    private Site buildSite() {
        Site site = new Site();
        site.setId(1L);
        site.setName("Gov Main");
        site.setCode("gov-main");
        site.setStatus("enabled");
        return site;
    }
}