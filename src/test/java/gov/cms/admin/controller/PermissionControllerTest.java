package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.PermissionTreeNode;
import gov.cms.admin.entity.Permission;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.PermissionService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PermissionController.class)
@Import(SecurityConfig.class)
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "sys:permission:view")
    void getPermissionTreeReturnsTreeForAuthorizedUser() throws Exception {
        Permission permission = buildPermission();
        PermissionTreeNode node = PermissionTreeNode.from(permission);
        when(permissionService.getPermissionTree()).thenReturn(List.of(node));

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sys:permission"))
                .andExpect(jsonPath("$[0].code").value("sys:permission"));
    }

    @Test
    @WithMockUser(authorities = "sys:permission:view")
    void createPermissionReturnsForbiddenWithoutCreateAuthority() throws Exception {
        Permission permission = buildPermission();

        mockMvc.perform(post("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(permission)))
                .andExpect(status().isForbidden());

        verify(permissionService, never()).createPermission(any());
    }

    @Test
    @WithMockUser(authorities = "sys:permission:create")
    void createPermissionReturnsCreatedPermissionForAuthorizedUser() throws Exception {
        Permission permission = buildPermission();
        when(permissionService.createPermission(any(Permission.class))).thenReturn(permission);

        mockMvc.perform(post("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(permission)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sys:permission"))
                .andExpect(jsonPath("$.name").value("权限管理"));
    }

    @Test
    @WithMockUser(authorities = "sys:permission:create")
    void createPermissionReturnsConflictWhenCodeExists() throws Exception {
        Permission permission = buildPermission();
        doAnswer(invocation -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "权限编码已存在");
        }).when(permissionService).createPermission(any(Permission.class));

        mockMvc.perform(post("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(permission)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("权限编码已存在"));
    }

    private Permission buildPermission() {
        Permission permission = new Permission();
        permission.setId("sys:permission");
        permission.setName("权限管理");
        permission.setCode("sys:permission");
        permission.setType("menu");
        permission.setParentId("sys");
        permission.setPath("/permissions");
        permission.setIcon("LockOutlined");
        permission.setSort(3);
        return permission;
    }
}