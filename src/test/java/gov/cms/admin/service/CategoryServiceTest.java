package gov.cms.admin.service;

import gov.cms.admin.dto.CategoryMoveRequest;
import gov.cms.admin.dto.CategoryRequest;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateBindingRepository templateBindingRepository;
    @InjectMocks private CategoryService categoryService;

    @Test
    void createCategoryBuildsRootPath() {
        CategoryRequest request = buildRequest();
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCase(1L, "news")).thenReturn(false);
        when(categoryRepository.existsBySiteIdAndFullPath(1L, "/news")).thenReturn(false);
        when(categoryRepository.existsSiblingName(1L, null, "新闻中心")).thenReturn(false);
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 11L, "column_list", "active")).thenReturn(List.of());
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 11L, "column_detail_default", "active")).thenReturn(List.of());
        doAnswer(invocation -> { Category category = invocation.getArgument(0); if (category.getId() == null) category.setId(11L); return category; }).when(categoryRepository).save(any(Category.class));

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
        request.setName("政策公开");

        Category parent = buildCategory(10L, null, "/news", 1, 1L);
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(parent));
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCase(1L, "policy")).thenReturn(false);
        when(categoryRepository.existsBySiteIdAndFullPath(1L, "/news/policy")).thenReturn(false);
        when(categoryRepository.existsSiblingName(1L, 10L, "政策公开")).thenReturn(false);
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 12L, "column_list", "active")).thenReturn(List.of());
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 12L, "column_detail_default", "active")).thenReturn(List.of());
        doAnswer(invocation -> { Category category = invocation.getArgument(0); if (category.getId() == null) category.setId(12L); return category; }).when(categoryRepository).save(any(Category.class));

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
    void createCategoryRejectsInvalidTemplateType() {
        CategoryRequest request = buildRequest();
        request.setListTemplateId(99L);
        Template template = new Template();
        template.setId(99L);
        template.setSiteId(1L);
        template.setType("content_detail");
        template.setStatus("active");
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        when(templateRepository.findById(99L)).thenReturn(Optional.of(template));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> categoryService.createCategory(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createCategorySyncsBindingsWhenTemplatesProvided() {
        CategoryRequest request = buildRequest();
        request.setListTemplateId(21L);
        request.setDetailTemplateId(22L);
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCase(1L, "news")).thenReturn(false);
        when(categoryRepository.existsBySiteIdAndFullPath(1L, "/news")).thenReturn(false);
        when(categoryRepository.existsSiblingName(1L, null, "新闻中心")).thenReturn(false);
        when(templateRepository.findById(21L)).thenReturn(Optional.of(buildTemplate(21L, "column_list")));
        when(templateRepository.findById(22L)).thenReturn(Optional.of(buildTemplate(22L, "content_detail")));
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 11L, "column_list", "active")).thenReturn(List.of());
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 11L, "column_detail_default", "active")).thenReturn(List.of());
        doAnswer(invocation -> { Category category = invocation.getArgument(0); if (category.getId() == null) category.setId(11L); return category; }).when(categoryRepository).save(any(Category.class));
        when(templateBindingRepository.countByTemplateIdAndStatus(21L, "active")).thenReturn(1L);
        when(templateBindingRepository.countByTemplateIdAndStatus(22L, "active")).thenReturn(1L);
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.createCategory(request);

        verify(templateBindingRepository, atLeastOnce()).save(any(TemplateBinding.class));
        verify(templateRepository, atLeastOnce()).save(any(Template.class));
    }

    @Test
    void updateCategoryClearsBindingsWhenTemplateRemoved() {
        Category existing = buildCategory(11L, null, "/news", 1, 1L);
        existing.setListTemplateId(21L);
        TemplateBinding activeBinding = new TemplateBinding();
        activeBinding.setId(51L);
        activeBinding.setTemplateId(21L);
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(existing));
        when(categoryRepository.findByIdAndSiteId(11L, 1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsBySiteIdAndCodeIgnoreCaseAndIdNot(1L, "news", 11L)).thenReturn(false);
        when(categoryRepository.existsBySiteIdAndFullPathAndIdNot(1L, "/news", 11L)).thenReturn(false);
        when(categoryRepository.existsSiblingNameExcludingId(1L, null, "新闻中心", 11L)).thenReturn(false);
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 11L, "column_list", "active")).thenReturn(List.of(activeBinding));
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 11L, "column_detail_default", "active")).thenReturn(List.of());
        when(templateBindingRepository.countByTemplateIdAndStatus(21L, "active")).thenReturn(0L);
        when(templateRepository.findById(21L)).thenReturn(Optional.of(buildTemplate(21L, "column_list")));
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryRequest request = buildRequest();
        request.setListTemplateId(null);

        categoryService.updateCategory(11L, request);

        assertEquals("inactive", activeBinding.getStatus());
        verify(templateBindingRepository).saveAll(any());
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
    void deleteCategoryClearsTemplateBindings() {
        Category category = buildCategory(1L, null, "/news", 1, 1L);
        category.setListTemplateId(21L);
        TemplateBinding activeBinding = new TemplateBinding();
        activeBinding.setTemplateId(21L);
        when(categoryRepository.findByIdAndSiteId(1L, 1L)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentId(1L)).thenReturn(0L);
        when(articleRepository.countByPrimaryCategoryId(1L)).thenReturn(0L);
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 1L, "column_list", "active")).thenReturn(List.of(activeBinding));
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "column", 1L, "column_detail_default", "active")).thenReturn(List.of());
        when(templateBindingRepository.countByTemplateIdAndStatus(21L, "active")).thenReturn(0L);
        when(templateRepository.findById(21L)).thenReturn(Optional.of(buildTemplate(21L, "column_list")));
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.deleteCategory(1L, 1L);

        assertEquals("inactive", activeBinding.getStatus());
        verify(categoryRepository).delete(category);
    }

    private CategoryRequest buildRequest() {
        CategoryRequest request = new CategoryRequest();
        request.setSiteId(1L);
        request.setName("新闻中心");
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
        category.setName("新闻中心");
        category.setCode("news");
        category.setType("channel");
        category.setSlug(fullPath.substring(fullPath.lastIndexOf('/') + 1));
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

    private Template buildTemplate(Long id, String type) {
        Template template = new Template();
        template.setId(id);
        template.setSiteId(1L);
        template.setType(type);
        template.setStatus("active");
        return template;
    }
}