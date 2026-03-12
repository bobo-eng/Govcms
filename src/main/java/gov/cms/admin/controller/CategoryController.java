package gov.cms.admin.controller;

import gov.cms.admin.dto.CategoryImpactResponse;
import gov.cms.admin.dto.CategoryMoveRequest;
import gov.cms.admin.dto.CategoryRequest;
import gov.cms.admin.dto.CategorySortRequest;
import gov.cms.admin.dto.CategoryStatusUpdateRequest;
import gov.cms.admin.dto.CategoryTreeNode;
import gov.cms.admin.entity.Category;
import gov.cms.admin.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('content:category:view')")
    public ResponseEntity<List<CategoryTreeNode>> getCategoryTree(
            @RequestParam Long siteId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(categoryService.getCategoryTree(siteId, keyword, status));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('content:category:view')")
    public ResponseEntity<List<Category>> getCategories(
            @RequestParam Long siteId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(categoryService.getCategories(siteId, parentId, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:view')")
    public ResponseEntity<Category> getCategory(@PathVariable Long id, @RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok(categoryService.getCategoryById(id, siteId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:category:create')")
    public ResponseEntity<Category> createCategory(@RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:update')")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @PutMapping("/{id}/sort")
    @PreAuthorize("hasAuthority('content:category:update')")
    public ResponseEntity<Category> updateSort(@PathVariable Long id, @RequestBody CategorySortRequest request) {
        return ResponseEntity.ok(categoryService.updateSort(id, request));
    }

    @PutMapping("/{id}/move")
    @PreAuthorize("hasAuthority('content:category:update')")
    public ResponseEntity<Category> moveCategory(@PathVariable Long id, @RequestBody CategoryMoveRequest request) {
        return ResponseEntity.ok(categoryService.moveCategory(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:category:update')")
    public ResponseEntity<Category> updateStatus(@PathVariable Long id, @RequestBody CategoryStatusUpdateRequest request) {
        return ResponseEntity.ok(categoryService.updateStatus(id, request));
    }

    @GetMapping("/{id}/impact")
    @PreAuthorize("hasAuthority('content:category:view')")
    public ResponseEntity<CategoryImpactResponse> getImpact(@PathVariable Long id, @RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok(categoryService.getImpact(id, siteId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:delete')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, @RequestParam Long siteId) {
        categoryService.deleteCategory(id, siteId);
        return ResponseEntity.noContent().build();
    }
}
