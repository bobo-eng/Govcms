package gov.cms.admin.service;

import gov.cms.admin.entity.MediaFile;
import gov.cms.admin.repository.MediaFileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MediaService {

    private static final String MEDIA_TYPE_IMAGE = "image";
    private static final String MEDIA_TYPE_DOCUMENT = "document";
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt");
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain")
    );

    private final MediaFileRepository mediaFileRepository;
    private final MediaStorageService mediaStorageService;

    public MediaService(MediaFileRepository mediaFileRepository, MediaStorageService mediaStorageService) {
        this.mediaFileRepository = mediaFileRepository;
        this.mediaStorageService = mediaStorageService;
    }

    public Page<MediaFile> getMediaFiles(String keyword, String type, Pageable pageable) {
        return mediaFileRepository.searchMediaFiles(keyword, normalizeType(type, true), pageable);
    }

    public MediaFile getMediaFileById(Long id) {
        return mediaFileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "媒体文件不存在"));
    }

    @Transactional
    public MediaFile upload(MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能超过 20MB");
        }

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        validateExtension(extension);

        StoredMediaObject storedMediaObject = mediaStorageService.store(file);

        MediaFile mediaFile = new MediaFile();
        mediaFile.setOriginalName(originalName);
        mediaFile.setStoredName(storedMediaObject.storedName());
        mediaFile.setStoragePath(storedMediaObject.storagePath());
        mediaFile.setMimeType(resolveMimeType(file, extension));
        mediaFile.setExtension(extension);
        mediaFile.setFileSize(file.getSize());
        mediaFile.setMediaType(resolveMediaType(extension));
        mediaFile.setUploadedBy(StringUtils.hasText(uploadedBy) ? uploadedBy : "unknown");

        return mediaFileRepository.save(mediaFile);
    }

    @Transactional
    public void deleteMediaFile(Long id) {
        MediaFile mediaFile = getMediaFileById(id);
        mediaFileRepository.delete(mediaFile);
        mediaStorageService.delete(mediaFile.getStoragePath());
    }

    public MediaPreviewResource loadPreviewResource(Long id) {
        MediaFile mediaFile = getMediaFileById(id);
        return new MediaPreviewResource(mediaFile, mediaStorageService.load(mediaFile.getStoragePath()));
    }

    private String normalizeOriginalName(String originalFilename) {
        String fileName = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename).trim();
        if (!StringUtils.hasText(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名无效");
        }

        int separatorIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String normalized = separatorIndex >= 0 ? fileName.substring(separatorIndex + 1) : fileName;
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名无效");
        }
        return normalized;
    }

    private String extractExtension(String originalName) {
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持上传图片或文档文件");
        }
        return originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        if (!IMAGE_EXTENSIONS.contains(extension) && !DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件类型不支持上传");
        }
    }

    private String resolveMediaType(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return MEDIA_TYPE_IMAGE;
        }
        return MEDIA_TYPE_DOCUMENT;
    }

    private String resolveMimeType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private String normalizeType(String type, boolean allowBlank) {
        if (!StringUtils.hasText(type)) {
            return allowBlank ? null : "";
        }

        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPE_IMAGE.equals(normalized) && !MEDIA_TYPE_DOCUMENT.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "媒体类型不支持");
        }
        return normalized;
    }
}