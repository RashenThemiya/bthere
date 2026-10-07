package com.jobhub.service.upload;

import com.jobhub.dto.upload.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ImageUploadService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.region}")
    private String region;

    @Value("${app.storage.s3.public-base-url:}")
    private String publicBaseUrl;

    public FileUploadResponse uploadImage(String keyPrefix, MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase();
        String key = keyPrefix + "/" + UUID.randomUUID() + EXTENSIONS.get(contentType);

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the uploaded image", exception);
        }
        if (!matchesContentType(imageBytes, contentType)) {
            throw new IllegalArgumentException(
                    "Image content does not match its JPG, PNG or WebP content type");
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(requireBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(imageBytes));

        return new FileUploadResponse(
                key,
                buildUrl(key),
                contentType,
                file.getSize()
        );
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image must not exceed 10 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !EXTENSIONS.containsKey(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only JPG, PNG and WebP images are allowed");
        }
    }

    private boolean matchesContentType(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private String requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS_S3_BUCKET is not configured");
        }
        return bucket;
    }

    private String buildUrl(String key) {
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replace("%2F", "/");
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "") + "/" + encodedKey;
        }
        return "https://" + bucket + ".s3." + region
                + ".amazonaws.com/" + encodedKey;
    }
}
