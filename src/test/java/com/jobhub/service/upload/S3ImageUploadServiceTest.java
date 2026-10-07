package com.jobhub.service.upload;

import com.jobhub.dto.upload.FileUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ImageUploadServiceTest {

    private S3Client s3Client;
    private S3ImageUploadService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        service = new S3ImageUploadService(s3Client);
        ReflectionTestUtils.setField(service, "bucket", "jobhub-images");
        ReflectionTestUtils.setField(service, "region", "eu-north-1");
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://cdn.example.com");
    }

    @Test
    void uploadsServiceIconWithStableUniqueKey() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.png", "image/png", png);

        FileUploadResponse response = service.uploadImage("service-icons", file);

        assertTrue(response.key().matches("service-icons/[0-9a-f-]+\\.png"));
        assertEquals("https://cdn.example.com/" + response.key(), response.url());
        assertEquals("image/png", response.contentType());
        assertEquals(png.length, response.size());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.gif", "image/gif", new byte[] {1, 2, 3});

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadImage("service-icons", file));

        assertEquals("Only JPG, PNG and WebP images are allowed", error.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.png", "image/png", new byte[] {1, 2, 3});

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadImage("service-icons", file));

        assertTrue(error.getMessage().contains("does not match"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsImagesLargerThanTenMegabytes() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L * 1024L * 1024L + 1L);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.uploadImage("service-icons", file));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
        verifyNoInteractions(s3Client);
    }
}
