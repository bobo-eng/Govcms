package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.PublishRequest;
import gov.cms.admin.dto.PublishRollbackRequest;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.PublishService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublishController.class)
@Import(SecurityConfig.class)
class PublishControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PublishService publishService;
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
    @WithMockUser(authorities = "publish:center:view")
    void checkReturnsOk() throws Exception {
        PublishRequest request = new PublishRequest();
        request.setSiteId(1L);
        request.setUnitType("content");
        request.setUnitIds(java.util.List.of(9L));
        request.setMode("incremental");

        mockMvc.perform(post("/api/publish/check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "publish:center:execute")
    void createJobReturnsCreated() throws Exception {
        PublishRequest request = new PublishRequest();
        request.setSiteId(1L);
        request.setUnitType("content");
        request.setUnitIds(java.util.List.of(9L));
        request.setMode("incremental");

        mockMvc.perform(post("/api/publish/jobs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "publish:center:rollback")
    void rollbackReturnsOk() throws Exception {
        PublishRollbackRequest request = new PublishRollbackRequest();
        request.setReason("Manual rollback");

        mockMvc.perform(post("/api/publish/jobs/{id}/rollback", 10L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "publish:center:artifact:view")
    void artifactsReturnsOk() throws Exception {
        mockMvc.perform(get("/api/publish/jobs/{id}/artifacts", 10L))
                .andExpect(status().isOk());
    }
}