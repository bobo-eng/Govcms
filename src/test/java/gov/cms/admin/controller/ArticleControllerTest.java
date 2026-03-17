package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.ArticleRejectRequest;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.ArticleService;
import gov.cms.admin.service.CustomUserDetailsService;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ArticleController.class)
@Import(SecurityConfig.class)
class ArticleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ArticleService articleService;
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
    @WithMockUser(authorities = "content:article:submit-review")
    void submitReviewReturnsOk() throws Exception {
        mockMvc.perform(post("/api/articles/{id}/submit-review", 9L))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "content:article:reject")
    void rejectReturnsOk() throws Exception {
        ArticleRejectRequest request = new ArticleRejectRequest();
        request.setReason("Need revision");

        mockMvc.perform(post("/api/articles/{id}/reject", 9L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "content:article:history:view")
    void historiesReturnsOk() throws Exception {
        when(articleService.listHistories(9L)).thenReturn(List.of());

        mockMvc.perform(get("/api/articles/{id}/histories", 9L))
                .andExpect(status().isOk());
    }
}
