package com.jobhub.service.upload;

import com.jobhub.dto.upload.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    public FileUploadResponse uploadProviderImage(Long userId, MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase();
        String key = "providers/" + userId + "/images/"
                + UUID.randomUUID() + EXTENSIONS.get(contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(requireBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();
        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the uploaded image", exception);
        }

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
            throw new IllegalArgumentException("Image must not exceed 10 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !EXTENSIONS.containsKey(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only JPG, PNG and WebP images are allowed");
        }
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
