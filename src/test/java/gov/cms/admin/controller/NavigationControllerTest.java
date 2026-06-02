package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.NavigationItemRequest;
import gov.cms.admin.dto.NavigationItemSortRequest;
import gov.cms.admin.entity.NavigationItem;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.NavigationService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NavigationController.class)
@Import(SecurityConfig.class)
class NavigationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private NavigationService navigationService;
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
    @WithMockUser(authorities = "navigation:manage:view")
    void getNavigationItemsReturnsOk() throws Exception {
        NavigationItem item = new NavigationItem();
        item.setId(1L);
        item.setName("导航一");
        item.setCode("nav-1");
        when(navigationService.getNavigationItems(1L, null, null)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/navigation").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("nav-1"));
    }

    @Test
    @WithMockUser(authorities = "navigation:manage:create")
    void createNavigationItemReturnsCreated() throws Exception {
        NavigationItemRequest request = new NavigationItemRequest();
        request.setSiteId(1L);
        request.setName("导航一");
        request.setCode("nav-1");
        request.setTargetType("category");
        request.setTargetId(10L);

        NavigationItem item = new NavigationItem();
        item.setId(1L);
        item.setCode("nav-1");
        when(navigationService.createNavigationItem(any())).thenReturn(item);

        mockMvc.perform(post("/api/navigation")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(authorities = "navigation:manage:update")
    void updateSortReturnsOk() throws Exception {
        NavigationItemSortRequest request = new NavigationItemSortRequest();
        request.setSiteId(1L);
        request.setSortOrder(2);
        when(navigationService.updateSort(anyLong(), any())).thenReturn(new NavigationItem());

        mockMvc.perform(put("/api/navigation/{id}/sort", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "navigation:manage:delete")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/navigation/{id}", 1L).param("siteId", "1"))
                .andExpect(status().isNoContent());
    }
}