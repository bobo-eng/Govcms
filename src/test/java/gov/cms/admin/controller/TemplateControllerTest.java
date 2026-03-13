package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.TemplateBindingRequest;
import gov.cms.admin.dto.TemplatePreviewRequest;
import gov.cms.admin.dto.TemplatePreviewResponse;
import gov.cms.admin.dto.TemplateRequest;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.TemplateService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TemplateController.class)
@Import(SecurityConfig.class)
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TemplateService templateService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "template:manage:view")
    void createTemplateReturnsForbiddenWithoutCreatePermission() throws Exception {
        TemplateRequest request = new TemplateRequest();
        request.setSiteId(1L);
        request.setName("Home");
        request.setCode("home-main");
        request.setType("home");
        request.setStatus("active");
        request.setLayoutSchema("{\"layout\":[]}");
        request.setBlockSchema("{\"blocks\":[]}");

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(templateService, never()).createTemplate(any());
    }

    @Test
    @WithMockUser(authorities = "template:manage:create")
    void createTemplateReturnsCreated() throws Exception {
        TemplateRequest request = new TemplateRequest();
        request.setSiteId(1L);
        request.setName("Home");
        request.setCode("home-main");
        request.setType("home");
        request.setStatus("active");
        request.setLayoutSchema("{\"layout\":[]}");
        request.setBlockSchema("{\"blocks\":[]}");

        Template template = new Template();
        template.setId(10L);
        template.setName("Home");
        when(templateService.createTemplate(any(TemplateRequest.class))).thenReturn(template);

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    @WithMockUser(authorities = "template:manage:create")
    void createTemplateReturnsConflict() throws Exception {
        TemplateRequest request = new TemplateRequest();
        request.setSiteId(1L);
        request.setName("Home");
        request.setCode("home-main");
        request.setType("home");
        request.setStatus("active");
        request.setLayoutSchema("{\"layout\":[]}");
        request.setBlockSchema("{\"blocks\":[]}");
        when(templateService.createTemplate(any(TemplateRequest.class))).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "duplicate"));

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "template:manage:preview")
    void previewReturnsBadRequest() throws Exception {
        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setSiteId(1L);
        request.setSourceType("content");
        when(templateService.previewTemplate(eq(10L), any(TemplatePreviewRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad preview"));

        mockMvc.perform(post("/api/templates/{id}/preview", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "template:manage:preview")
    void previewReturnsRenderedHtmlSnapshot() throws Exception {
        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setSiteId(1L);
        request.setSourceType("content");
        request.setSourceId(99L);

        TemplatePreviewResponse response = new TemplatePreviewResponse();
        response.setTemplateId(10L);
        response.setPageType("content-detail");
        response.setRenderedHtml("<html><body>preview</body></html>");
        response.setRenderEngine("thymeleaf");
        response.setRenderTemplateName("portal/page/content-detail");
        when(templateService.previewTemplate(eq(10L), any(TemplatePreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/templates/{id}/preview", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(10L))
                .andExpect(jsonPath("$.pageType").value("content-detail"))
                .andExpect(jsonPath("$.renderedHtml").value("<html><body>preview</body></html>"))
                .andExpect(jsonPath("$.renderEngine").value("thymeleaf"))
                .andExpect(jsonPath("$.renderTemplateName").value("portal/page/content-detail"));
    }

    @Test
    @WithMockUser(authorities = "template:manage:bind")
    void createBindingReturnsCreated() throws Exception {
        TemplateBindingRequest request = new TemplateBindingRequest();
        request.setSiteId(1L);
        request.setTargetType("site");
        request.setTargetId(1L);
        request.setBindingSlot("site_home");
        TemplateBinding binding = new TemplateBinding();
        binding.setId(88L);
        when(templateService.createBinding(eq(10L), any(TemplateBindingRequest.class))).thenReturn(binding);

        mockMvc.perform(post("/api/templates/{id}/bindings", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(88L));
    }

    @Test
    @WithMockUser(authorities = "template:manage:view")
    void getImpactReturnsOk() throws Exception {
        when(templateService.getImpact(10L, 1L)).thenReturn(new gov.cms.admin.dto.TemplateImpactResponse());

        mockMvc.perform(get("/api/templates/{id}/impact", 10L).param("siteId", "1"))
                .andExpect(status().isOk());
    }
}