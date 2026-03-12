package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.CategoryMoveRequest;
import gov.cms.admin.dto.CategoryRequest;
import gov.cms.admin.dto.CategoryStatusUpdateRequest;
import gov.cms.admin.dto.CategoryTreeNode;
import gov.cms.admin.entity.Category;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CategoryService;
import gov.cms.admin.service.CustomUserDetailsService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

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
    @WithMockUser(authorities = "content:category:view")
    void getCategoryTreeReturnsDataForAuthorizedUser() throws Exception {
        CategoryTreeNode node = new CategoryTreeNode();
        node.setId(1L);
        node.setName("????");
        node.setCode("news");
        when(categoryService.getCategoryTree(1L, null, null)).thenReturn(List.of(node));

        mockMvc.perform(get("/api/categories/tree").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("news"));
    }

    @Test
    @WithMockUser(authorities = "content:category:view")
    void createCategoryReturnsForbiddenWithoutCreatePermission() throws Exception {
        CategoryRequest request = new CategoryRequest();
        request.setSiteId(1L);
        request.setName("????");
        request.setCode("news");
        request.setSlug("news");
        request.setType("channel");

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).createCategory(any());
    }

    @Test
    @WithMockUser(authorities = "content:category:create")
    void createCategoryReturnsCreatedForAuthorizedUser() throws Exception {
        CategoryRequest request = new CategoryRequest();
        request.setSiteId(1L);
        request.setName("????");
        request.setCode("news");
        request.setSlug("news");
        request.setType("channel");

        Category category = new Category();
        category.setId(1L);
        category.setSiteId(1L);
        category.setName("????");
        category.setCode("news");
        category.setSlug("news");
        category.setFullPath("/news");
        when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(category);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPath").value("/news"));
    }

    @Test
    @WithMockUser(authorities = "content:category:update")
    void moveCategoryReturnsConflictWhenServiceRejects() throws Exception {
        CategoryMoveRequest request = new CategoryMoveRequest();
        request.setSiteId(1L);
        request.setTargetParentId(2L);

        when(categoryService.moveCategory(eq(1L), any(CategoryMoveRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "??????????????"));

        mockMvc.perform(put("/api/categories/{id}/move", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "content:category:update")
    void updateStatusReturnsSuccess() throws Exception {
        CategoryStatusUpdateRequest request = new CategoryStatusUpdateRequest();
        request.setSiteId(1L);
        request.setStatus("disabled");

        Category category = new Category();
        category.setId(1L);
        category.setStatus("disabled");
        when(categoryService.updateStatus(eq(1L), any(CategoryStatusUpdateRequest.class))).thenReturn(category);

        mockMvc.perform(put("/api/categories/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));
    }

    @Test
    @WithMockUser(authorities = "content:category:delete")
    void deleteCategoryReturnsConflictWhenBlocked() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "??????????????"))
                .when(categoryService).deleteCategory(anyLong(), anyLong());

        mockMvc.perform(delete("/api/categories/{id}", 1L).param("siteId", "1"))
                .andExpect(status().isConflict());
    }
}
