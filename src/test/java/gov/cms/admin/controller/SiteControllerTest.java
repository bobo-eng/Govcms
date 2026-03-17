package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.SiteOptionDto;
import gov.cms.admin.entity.Site;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.SiteService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SiteController.class)
@Import(SecurityConfig.class)
class SiteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SiteService siteService;
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
    @WithMockUser(authorities = "site:manage:view")
    void getSitesReturnsPageForAuthorizedUser() throws Exception {
        Site site = new Site();
        site.setId(1L);
        site.setName("Gov Main");
        site.setCode("gov-main");
        site.setStatus("enabled");
        when(siteService.getSites(nullable(String.class), nullable(String.class), nullable(Long.class), any())).thenReturn(new PageImpl<>(List.of(site), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("gov-main"));
    }

    @Test
    @WithMockUser(authorities = "content:article:view")
    void getSiteOptionsReturnsListForContentUsers() throws Exception {
        when(siteService.getSiteOptions()).thenReturn(List.of(new SiteOptionDto(1L, "Gov Main", "enabled")));

        mockMvc.perform(get("/api/sites/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Gov Main"));
    }

    @Test
    void getSiteOptionsReturnsUnauthorizedWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/sites/options"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "site:manage:view")
    void createSiteReturnsForbiddenWithoutCreatePermission() throws Exception {
        Site site = new Site();
        site.setName("Gov Main");
        site.setCode("gov-main");

        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(site)))
                .andExpect(status().isForbidden());

        verify(siteService, never()).createSite(any());
    }

    @Test
    @WithMockUser(authorities = "site:manage:delete")
    void deleteSiteReturnsNoContentForAuthorizedUser() throws Exception {
        mockMvc.perform(delete("/api/sites/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(siteService).deleteSite(1L);
    }
}