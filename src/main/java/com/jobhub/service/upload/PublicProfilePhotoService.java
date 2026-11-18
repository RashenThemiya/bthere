package com.jobhub.service.upload;

import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Locale;
import java.util.Set;

/**
 * Serves a user's current profile photo without credentials. Only an image that the customer or provider with that
 * user id has set as their profile photo is returned; other files under the private image prefix stay private.
 */
@Service
@RequiredArgsConstructor
public class PublicProfilePhotoService {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String NOT_FOUND = "Profile photo not found";

    private final CustomerRepository customers;
    private final ServiceProviderRepository providers;
    private final ProfilePhotoUrls urls;
    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket:}")
    private String bucket;

    public record Photo(byte[] bytes, String contentType) {}

    @Transactional(readOnly = true)
    public Photo load(Long userId, String file) {
        if (userId == null || userId < 1 || file == null) throw new ResourceNotFoundException(NOT_FOUND);
        String key = "providers/" + userId + "/images/" + file;
        if (!key.equals(urls.key(key)) || !isCurrentProfilePhoto(userId, key)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        if (bucket == null || bucket.isBlank()) throw new IllegalStateException("AWS_S3_BUCKET is not configured");
        try {
            var object = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            String type = object.response().contentType() == null ? ""
                    : object.response().contentType().toLowerCase(Locale.ROOT);
            if (!TYPES.contains(type)) throw new ResourceNotFoundException(NOT_FOUND);
            return new Photo(object.asByteArray(), type);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) throw new ResourceNotFoundException(NOT_FOUND);
            throw exception;
        }
    }

    private boolean isCurrentProfilePhoto(Long userId, String key) {
        return customers.findByUserId(userId).map(c -> key.equals(urls.key(c.getProfilePhoto()))).orElse(false)
                || providers.findByUserId(userId).map(p -> key.equals(urls.key(p.getProfilePhoto()))).orElse(false);
    }
}
