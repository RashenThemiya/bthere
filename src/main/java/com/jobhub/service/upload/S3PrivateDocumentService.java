package com.jobhub.service.upload;

import com.jobhub.dto.upload.FileUploadResponse;
import com.jobhub.dto.upload.PresignedUrlResponse;
import com.jobhub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PrivateDocumentService {

    private static final long MAX_DOCUMENT_BYTES = 100L * 1024L * 1024L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    public FileUploadResponse upload(Long userId, MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase();
        String key = "providers/" + userId + "/documents/"
                + UUID.randomUUID() + EXTENSIONS.get(contentType);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(requireBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the uploaded document", exception);
        }
        return new FileUploadResponse(
                key,
                "s3://" + bucket + "/" + key,
                contentType,
                file.getSize()
        );
    }

    public PresignedUrlResponse createViewUrl(AuthenticatedUser user, String key) {
        String normalizedKey = normalizeKey(key);
        boolean admin = user.roles().stream().anyMatch(role ->
                "SUPER_ADMIN".equals(role) || role.endsWith("_ADMIN") || "ADMIN".equals(role)
        );
        if (!admin && !normalizedKey.startsWith("providers/" + user.id() + "/")) {
            throw new IllegalArgumentException("You cannot access this file");
        }

        Duration duration = Duration.ofMinutes(10);
        String url = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(duration)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(requireBucket())
                                .key(normalizedKey)
                                .build())
                        .build()
        ).url().toString();
        return new PresignedUrlResponse(
                normalizedKey,
                url,
                Instant.now().plus(duration)
        );
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is required");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Document must not exceed 100 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !EXTENSIONS.containsKey(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only PDF, JPG, PNG and WebP files are allowed");
        }
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("File key is required");
        }
        String prefix = "s3://" + requireBucket() + "/";
        String result = value.startsWith(prefix) ? value.substring(prefix.length()) : value;
        if (result.contains("..") || result.startsWith("/") || !result.startsWith("providers/")) {
            throw new IllegalArgumentException("File key is invalid");
        }
        return result;
    }

    private String requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS_S3_BUCKET is not configured");
        }
        return bucket;
    }
}
