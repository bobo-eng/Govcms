package gov.cms.admin.controller;

import gov.cms.admin.entity.MediaFile;
import gov.cms.admin.service.MediaPreviewResource;
import gov.cms.admin.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('media:manage:view')")
    public ResponseEntity<Page<MediaFile>> getMediaFiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "type") String type,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mediaService.getMediaFiles(keyword, type, pageable));
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('media:manage:upload')")
    public ResponseEntity<MediaFile> uploadMediaFile(@RequestParam("file") MultipartFile file, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.upload(file, username));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('media:manage:delete')")
    public ResponseEntity<Void> deleteMediaFile(@PathVariable Long id) {
        mediaService.deleteMediaFile(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('media:manage:view')")
    public ResponseEntity<Resource> previewMediaFile(@PathVariable Long id) {
        MediaPreviewResource previewResource = mediaService.loadPreviewResource(id);
        MediaFile mediaFile = previewResource.mediaFile();
        MediaType mediaType = resolveMediaType(mediaFile.getMimeType());
        boolean inline = "image".equals(mediaFile.getMediaType()) || "pdf".equalsIgnoreCase(mediaFile.getExtension());
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(mediaFile.getOriginalName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(mediaFile.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(previewResource.resource());
    }

    private MediaType resolveMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}