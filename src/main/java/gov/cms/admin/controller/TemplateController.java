package gov.cms.admin.controller;

import gov.cms.admin.dto.TemplateBindingRequest;
import gov.cms.admin.dto.TemplateImpactResponse;
import gov.cms.admin.dto.TemplatePreviewRequest;
import gov.cms.admin.dto.TemplatePreviewResponse;
import gov.cms.admin.dto.TemplateRequest;
import gov.cms.admin.dto.TemplateStatusUpdateRequest;
import gov.cms.admin.dto.TemplateVersionRequest;
import gov.cms.admin.dto.TemplateVersionRollbackRequest;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.entity.TemplateVersion;
import gov.cms.admin.service.TemplateService;
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
@RequestMapping("/api/templates")
@CrossOrigin(origins = "*")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('template:manage:view')")
    public ResponseEntity<List<Template>> getTemplates(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(templateService.getTemplates(siteId, type, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('template:manage:view')")
    public ResponseEntity<Template> getTemplate(@PathVariable Long id, @RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok(templateService.getTemplateById(id, siteId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('template:manage:create')")
    public ResponseEntity<Template> createTemplate(@RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('template:manage:update')")
    public ResponseEntity<Template> updateTemplate(@PathVariable Long id, @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('template:manage:update')")
    public ResponseEntity<Template> updateStatus(@PathVariable Long id, @RequestBody TemplateStatusUpdateRequest request) {
        return ResponseEntity.ok(templateService.updateStatus(id, request));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('template:manage:view')")
    public ResponseEntity<List<TemplateVersion>> getVersions(@PathVariable Long id, @RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok(templateService.getVersions(id, siteId));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('template:manage:update')")
    public ResponseEntity<TemplateVersion> createVersion(@PathVariable Long id, @RequestBody TemplateVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createVersion(id, request));
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAuthority('template:manage:update')")
    public ResponseEntity<Template> rollbackVersion(@PathVariable Long id, @RequestBody TemplateVersionRollbackRequest request) {
        return ResponseEntity.ok(templateService.rollbackVersion(id, request));
    }

    @PostMapping("/{id}/bindings")
    @PreAuthorize("hasAuthority('template:manage:bind')")
    public ResponseEntity<TemplateBinding> createBinding(@PathVariable Long id, @RequestBody TemplateBindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createBinding(id, request));
    }

    @GetMapping("/{id}/bindings")
    @PreAuthorize("hasAuthority('template:manage:view')")
    public ResponseEntity<List<TemplateBinding>> getBindings(
            @PathVariable Long id,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(templateService.getBindings(id, siteId, targetType, status));
    }

    @DeleteMapping("/bindings/{bindingId}")
    @PreAuthorize("hasAuthority('template:manage:bind')")
    public ResponseEntity<Void> deleteBinding(@PathVariable Long bindingId, @RequestParam Long siteId) {
        templateService.deleteBinding(bindingId, siteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('template:manage:preview')")
    public ResponseEntity<TemplatePreviewResponse> previewTemplate(@PathVariable Long id, @RequestBody TemplatePreviewRequest request) {
        return ResponseEntity.ok(templateService.previewTemplate(id, request));
    }

    @GetMapping("/{id}/impact")
    @PreAuthorize("hasAuthority('template:manage:view')")
    public ResponseEntity<TemplateImpactResponse> getImpact(@PathVariable Long id, @RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok(templateService.getImpact(id, siteId));
    }
}
