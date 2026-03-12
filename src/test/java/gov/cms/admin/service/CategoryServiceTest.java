package gov.cms.admin.service;

import gov.cms.admin.dto.CategoryMoveRequest;
import gov.cms.admin.dto.CategoryRequest;
import gov.cms.admin.entity.Category;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategoryBuildsRootPath() {
        CategoryRequest request = buildRequest();
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCase(1L, "news")).thenReturn(false);
        when(categoryRepository.existsBySiteIdAndFullPath(1L, "/news")).thenReturn(false);
        when(categoryRepository.existsSiblingName(1L, null, "????")).thenReturn(false);
        doAnswer(invocation -> invocation.getArgument(0)).when(categoryRepository).save(any(Category.class));

        Category saved = categoryService.createCategory(request);

        assertEquals("/news", saved.getFullPath());
        assertEquals(1, saved.getLevel());
    }

    @Test
    void createCategoryBuildsChildPath() {
        CategoryRequest request = buildRequest();
        request.setParentId(10L);
        request.setCode("policy");
        request.setSlug("policy");
        request.setName("????");

        Category parent = buildCategory(10L, null, "/news", 1, 1L);
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(parent));
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCase(1L, "policy")).thenReturn(false);
        when(categoryRepository.existsBySiteIdAndFullPath(1L, "/news/policy")).thenReturn(false);
        when(categoryRepository.existsSiblingName(1L, 10L, "????")).thenReturn(false);
        doAnswer(invocation -> invocation.getArgument(0)).when(categoryRepository).save(any(Category.class));

        Category saved = categoryService.createCategory(request);

        assertEquals("/news/policy", saved.getFullPath());
        assertEquals(2, saved.getLevel());
    }

    @Test
    void createCategoryRejectsDuplicateCode() {
        CategoryRequest request = buildRequest();
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCase(1L, "news")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> categoryService.createCategory(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void moveCategoryRejectsMovingIntoDescendant() {
        Category root = buildCategory(1L, null, "/news", 1, 1L);
        Category child = buildCategory(2L, 1L, "/news/policy", 2, 1L);

        CategoryMoveRequest request = new CategoryMoveRequest();
        request.setSiteId(1L);
        request.setTargetParentId(2L);

        when(categoryRepository.findByIdAndSiteId(1L, 1L)).thenReturn(Optional.of(root));
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(root, child));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> categoryService.moveCategory(1L, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void moveCategoryRefreshesDescendantPath() {
        Category root = buildCategory(1L, null, "/news", 1, 1L);
        Category child = buildCategory(2L, 1L, "/news/policy", 2, 1L);
        child.setSlug("policy");
        Category grandChild = buildCategory(3L, 2L, "/news/policy/guide", 3, 1L);
        grandChild.setSlug("guide");

        CategoryMoveRequest request = new CategoryMoveRequest();
        request.setSiteId(1L);
        request.setTargetParentId(null);

        when(categoryRepository.findByIdAndSiteId(2L, 1L)).thenReturn(Optional.of(child));
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(root, child, grandChild));
        doAnswer(invocation -> invocation.getArgument(0)).when(categoryRepository).save(any(Category.class));

        categoryService.moveCategory(2L, request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(item -> item.getId().equals(3L) && "/policy/guide".equals(item.getFullPath())));
    }

    @Test
    void deleteCategoryRejectsWhenChildrenExist() {
        Category category = buildCategory(1L, null, "/news", 1, 1L);
        when(categoryRepository.findByIdAndSiteId(1L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentId(1L)).thenReturn(1L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> categoryService.deleteCategory(1L, 1L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void deleteCategoryRejectsWhenArticlesExist() {
        Category category = buildCategory(1L, null, "/news", 1, 1L);
        when(categoryRepository.findByIdAndSiteId(1L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentId(1L)).thenReturn(0L);
        when(articleRepository.countByPrimaryCategoryId(1L)).thenReturn(2L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> categoryService.deleteCategory(1L, 1L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private CategoryRequest buildRequest() {
        CategoryRequest request = new CategoryRequest();
        request.setSiteId(1L);
        request.setName("????");
        request.setCode("news");
        request.setType("channel");
        request.setSlug("news");
        request.setStatus("enabled");
        request.setSortOrder(1);
        request.setAggregationMode("manual");
        request.setNavVisible(true);
        request.setBreadcrumbVisible(true);
        request.setPublicVisible(true);
        return request;
    }

    private Category buildCategory(Long id, Long parentId, String fullPath, int level, Long siteId) {
        Category category = new Category();
        category.setId(id);
        category.setSiteId(siteId);
        category.setParentId(parentId);
        category.setName("??" + id);
        category.setCode("code" + id);
        category.setType("channel");
        category.setSlug("slug" + id);
        category.setFullPath(fullPath);
        category.setLevel(level);
        category.setSortOrder(level);
        category.setStatus("enabled");
        category.setAggregationMode("manual");
        category.setNavVisible(true);
        category.setBreadcrumbVisible(true);
        category.setPublicVisible(true);
        return category;
    }
}
