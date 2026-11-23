package com.jobhub.service.chat;

import com.jobhub.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Private S3 storage for chat files. Objects are never public; viewers get 15-minute signed GET URLs. */
@Component
@RequiredArgsConstructor
public class ChatAttachmentStorage {
    static final Duration URL_TTL = Duration.ofMinutes(15);

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${app.storage.s3.bucket:}")
    private String bucket;

    public record SignedUrl(String url, Instant expiresAt) {}

    public void put(String key, byte[] bytes, String mimeType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket()).key(key).contentType(mimeType)
                .contentLength((long) bytes.length).cacheControl("private, max-age=900").build(), RequestBody.fromBytes(bytes));
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
    }

    /** Images open inline; documents download with their display name. */
    public SignedUrl signedUrl(String key, String mimeType, String fileName, boolean inline) {
        var disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8).build().toString();
        var request = GetObjectRequest.builder().bucket(bucket()).key(key)
                .responseContentType(mimeType).responseContentDisposition(disposition).build();
        String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(URL_TTL).getObjectRequest(request).build()).url().toString();
        return new SignedUrl(url, Instant.now().plus(URL_TTL));
    }

    private String bucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_UNAVAILABLE", "File storage is not configured");
        }
        return bucket;
    }
}
