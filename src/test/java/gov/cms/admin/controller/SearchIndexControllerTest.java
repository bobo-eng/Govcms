package gov.cms.admin.controller;

import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.SearchIndexStatusResponse;
import gov.cms.admin.dto.SearchKeywordStatItem;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.SearchIndexService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchIndexController.class)
@Import(SecurityConfig.class)
class SearchIndexControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SearchIndexService searchIndexService;
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
    @WithMockUser(authorities = "search:ops:view")
    void statusReturnsOk() throws Exception {
        SearchIndexStatusResponse response = new SearchIndexStatusResponse();
        response.setLowResultKeywords(List.of(new SearchKeywordStatItem("政策", 3)));
        when(searchIndexService.getStatus(anyLong(), anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/search-index/status").param("siteId", "1").param("days", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowResultKeywords[0].keyword").value("政策"));
    }

    @Test
    @WithMockUser(authorities = "search:ops:rebuild")
    void rebuildSiteReturnsOk() throws Exception {
        when(searchIndexService.getStatus(anyLong(), anyInt(), anyInt())).thenReturn(new SearchIndexStatusResponse());

        mockMvc.perform(post("/api/search-index/rebuild/site/1").param("days", "14"))
                .andExpect(status().isOk());
    }
}
