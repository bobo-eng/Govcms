package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.entity.User;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserService userService;
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
    @WithMockUser(authorities = "sys:user:view")
    void getUserRolesReturnsOk() throws Exception {
        when(userService.getRoleIds(1L)).thenReturn(Set.of(10L, 11L));

        mockMvc.perform(get("/api/users/{id}/roles", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    @WithMockUser(authorities = "sys:user:update")
    void assignRolesReturnsOk() throws Exception {
        User user = new User();
        user.setId(1L);
        when(userService.assignRoles(1L, Set.of(10L))).thenReturn(user);

        mockMvc.perform(put("/api/users/{id}/roles", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Set.of(10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }
}
