package gov.cms.admin.controller;

import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthSummaryController.class)
@Import(SecurityConfig.class)
class HealthSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private org.springframework.boot.actuate.health.HealthEndpoint healthEndpoint;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "ROLE_admin,200",
        "ROLE_site_admin,200",
        "ROLE_editor,403",
        "ROLE_reviewer,403",
        "ROLE_publisher,403"
    })
    void accessControl(String authority, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/health/summary")
                .with(SecurityMockMvcRequestPostProcessors.user("test")
                    .authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().is(expectedStatus));
    }
}
