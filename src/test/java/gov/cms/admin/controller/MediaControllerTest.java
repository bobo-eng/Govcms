package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.entity.MediaFile;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.MediaService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@Import(SecurityConfig.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MediaService mediaService;

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
    @WithMockUser(authorities = "media:manage:view")
    void getMediaFilesReturnsPageForAuthorizedUser() throws Exception {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1L);
        mediaFile.setOriginalName("cover.png");
        mediaFile.setMimeType("image/png");
        mediaFile.setExtension("png");
        mediaFile.setFileSize(2048L);
        mediaFile.setMediaType("image");
        mediaFile.setUploadedBy("admin");

        when(mediaService.getMediaFiles(nullable(String.class), nullable(String.class), any()))
                .thenReturn(new PageImpl<>(List.of(mediaFile), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalName").value("cover.png"))
                .andExpect(jsonPath("$.content[0].mediaType").value("image"));
    }

    @Test
    @WithMockUser(authorities = "media:manage:view")
    void uploadReturnsForbiddenWithoutUploadPermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                MediaType.IMAGE_PNG_VALUE,
                "png-data".getBytes()
        );

        mockMvc.perform(multipart("/api/media/upload").file(file))
                .andExpect(status().isForbidden());

        verify(mediaService, never()).upload(any(), anyString());
    }

    @Test
    @WithMockUser(authorities = "media:manage:delete")
    void deleteReturnsNotFoundWhenMediaMissing() throws Exception {
        doAnswer(invocation -> {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "媒体文件不存在");
        }).when(mediaService).deleteMediaFile(anyLong());

        mockMvc.perform(delete("/api/media/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("媒体文件不存在"));
    }
}