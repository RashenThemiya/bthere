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
        return uploadToPrefix("providers/" + userId + "/documents/", file);
    }

    public FileUploadResponse uploadCustomer(Long userId, MultipartFile file) {
        return uploadToPrefix("customers/" + userId + "/documents/", file);
    }

    private FileUploadResponse uploadToPrefix(String prefix, MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase();
        String key = prefix
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
        boolean ownProvider = normalizedKey.startsWith("providers/" + user.id() + "/");
        boolean ownCustomer = user.roles().contains("CUSTOMER")
                && normalizedKey.startsWith("customers/" + user.id() + "/documents/");
        if (!admin && !ownProvider && !ownCustomer) {
            throw new IllegalArgumentException("You cannot access this file");
        }

        return signedViewUrl(normalizedKey);
    }

    /** Only call after resolving an approved, explicitly customer-visible image answer. */
    public PresignedUrlResponse createPublishedProviderImageUrl(Long userId, String key) {
        if (userId == null || key == null
                || !key.matches("providers/" + userId + "/(?:images|documents)/[A-Za-z0-9_-]+\\.(?:jpg|png|webp)")) {
            throw new IllegalArgumentException("Published image must belong to this provider");
        }
        try {
            var head = s3Client.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(requireBucket()).key(key).build());
            if (!java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(
                    head.contentType() == null ? "" : head.contentType().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Published file must be a JPG, PNG or WebP image");
            }
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) throw new IllegalArgumentException("Uploaded image does not exist");
            throw exception;
        }
        return signedViewUrl(key);
    }

    /** Only call for approved, explicitly customer-visible document/file answers. */
    public PresignedUrlResponse createPublishedProviderFileUrl(Long userId, String key) {
        if (userId == null || key == null
                || !key.matches("providers/" + userId + "/(?:images|documents)/[A-Za-z0-9_-]+\\.(?:pdf|jpg|png|webp)")) {
            throw new IllegalArgumentException("Published file must belong to this provider");
        }
        try {
            var head = s3Client.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(requireBucket()).key(key).build());
            if (head.contentType() == null || !EXTENSIONS.containsKey(head.contentType().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Published file must be PDF, JPG, PNG or WebP");
            }
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) throw new IllegalArgumentException("Uploaded file does not exist");
            throw exception;
        }
        return signedViewUrl(key);
    }

    private PresignedUrlResponse signedViewUrl(String normalizedKey) {
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
        if (result.contains("..") || result.startsWith("/")
                || !(result.startsWith("providers/") || result.startsWith("customers/"))) {
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

    public record CustomerFile(String contentType, long size) {}

    public CustomerFile inspectProviderFile(Long userId, String key) {
        if (key == null || key.contains("..")
                || !key.matches("providers/" + userId + "/documents/[A-Za-z0-9/_-]+\\.[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("File must be a private document owned by this provider");
        }
        try {
            var head = s3Client.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(requireBucket()).key(key).build());
            return new CustomerFile(head.contentType(), head.contentLength());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) throw new IllegalArgumentException("Uploaded file does not exist");
            throw exception;
        }
    }

    public CustomerFile inspectCustomerFile(Long userId, String key) {
        if (key == null || key.contains("..")
                || !key.matches("customers/" + userId + "/documents/[A-Za-z0-9_-]+\\.[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("File must be a private document owned by this customer");
        }
        try {
            var head = s3Client.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(requireBucket()).key(key).build());
            return new CustomerFile(head.contentType(), head.contentLength());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) throw new IllegalArgumentException("Uploaded file does not exist");
            throw exception;
        }
    }
}
