package gov.cms.admin.controller;

import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.PortalSearchCategoryItem;
import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.dto.SearchSuggestionItem;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PortalSearchController.class)
@Import(SecurityConfig.class)
class PortalSearchControllerTest {

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
    void searchReturnsOk() throws Exception {
        when(searchIndexService.search(anyLong(), anyString(), anyInt(), anyInt(), nullable(String.class), nullable(Long.class))).thenReturn(new PortalSearchResponse());

        mockMvc.perform(get("/api/portal/search").param("siteId", "1").param("keyword", "新闻"))
                .andExpect(status().isOk());
    }

    @Test
    void suggestionsReturnsOk() throws Exception {
        when(searchIndexService.listSuggestions(1L, "政", 8, 7)).thenReturn(List.of(new SearchSuggestionItem("政务公开", "popular", 5L)));

        mockMvc.perform(get("/api/portal/search/suggestions").param("siteId", "1").param("keyword", "政"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyword").value("政务公开"))
                .andExpect(jsonPath("$[0].source").value("popular"));
    }

    @Test
    void categoriesReturnsOk() throws Exception {
        when(searchIndexService.listCategories(1L)).thenReturn(List.of(new PortalSearchCategoryItem(9L, "新闻")));

        mockMvc.perform(get("/api/portal/search/categories").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].name").value("新闻"));
    }
}
