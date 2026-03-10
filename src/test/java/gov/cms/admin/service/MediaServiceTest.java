package gov.cms.admin.service;

import gov.cms.admin.entity.MediaFile;
import gov.cms.admin.repository.MediaFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadMediaStoresImageAndMetadata() {
        MediaFileRepository mediaFileRepository = mock(MediaFileRepository.class);
        LocalMediaStorageService storageService = new LocalMediaStorageService(tempDir.toString());
        storageService.initializeStorageDirectory();
        MediaService mediaService = new MediaService(mediaFileRepository, storageService);

        doAnswer(invocation -> {
            MediaFile mediaFile = invocation.getArgument(0);
            mediaFile.setId(1L);
            return mediaFile;
        }).when(mediaFileRepository).save(any(MediaFile.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                "png-data".getBytes()
        );

        MediaFile saved = mediaService.upload(file, "admin");

        assertNotNull(saved.getId());
        assertEquals("cover.png", saved.getOriginalName());
        assertEquals("image", saved.getMediaType());
        assertEquals("admin", saved.getUploadedBy());
        assertTrue(Files.exists(tempDir.resolve(saved.getStoragePath())));
    }

    @Test
    void uploadRejectsUnsupportedType() {
        MediaFileRepository mediaFileRepository = mock(MediaFileRepository.class);
        LocalMediaStorageService storageService = new LocalMediaStorageService(tempDir.toString());
        storageService.initializeStorageDirectory();
        MediaService mediaService = new MediaService(mediaFileRepository, storageService);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "bad".getBytes()
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> mediaService.upload(file, "admin"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void deleteMediaRemovesStoredFile() throws Exception {
        MediaFileRepository mediaFileRepository = mock(MediaFileRepository.class);
        LocalMediaStorageService storageService = new LocalMediaStorageService(tempDir.toString());
        storageService.initializeStorageDirectory();
        MediaService mediaService = new MediaService(mediaFileRepository, storageService);

        Path storedFile = tempDir.resolve("stored-file.pdf");
        Files.writeString(storedFile, "pdf-data");

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(3L);
        mediaFile.setOriginalName("manual.pdf");
        mediaFile.setStoredName("stored-file.pdf");
        mediaFile.setStoragePath("stored-file.pdf");
        mediaFile.setMimeType("application/pdf");
        mediaFile.setExtension("pdf");
        mediaFile.setFileSize(8L);
        mediaFile.setMediaType("document");
        mediaFile.setUploadedBy("admin");

        when(mediaFileRepository.findById(3L)).thenReturn(Optional.of(mediaFile));

        mediaService.deleteMediaFile(3L);

        verify(mediaFileRepository).delete(mediaFile);
        assertTrue(Files.notExists(storedFile));
    }

    @Test
    void deleteMediaRejectsMissingRecord() {
        MediaFileRepository mediaFileRepository = mock(MediaFileRepository.class);
        LocalMediaStorageService storageService = new LocalMediaStorageService(tempDir.toString());
        storageService.initializeStorageDirectory();
        MediaService mediaService = new MediaService(mediaFileRepository, storageService);

        when(mediaFileRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> mediaService.deleteMediaFile(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}