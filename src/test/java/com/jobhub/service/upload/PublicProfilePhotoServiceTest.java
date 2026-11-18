package com.jobhub.service.upload;

import com.jobhub.entity.customer.Customer;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PublicProfilePhotoServiceTest {
    private static final String FILE = "3f1c2a9e-1b2c-4d5e-8f90-123456789abc.jpg";

    private CustomerRepository customers;
    private ServiceProviderRepository providers;
    private S3Client s3;
    private PublicProfilePhotoService service;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        providers = mock(ServiceProviderRepository.class);
        s3 = mock(S3Client.class);
        var urls = new ProfilePhotoUrls("jobhube", "eu-north-1", "https://jobhube.s3.eu-north-1.amazonaws.com", "https://api.test");
        service = new PublicProfilePhotoService(customers, providers, urls, s3);
        ReflectionTestUtils.setField(service, "bucket", "jobhube");
        when(providers.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(customers.findByUserId(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void servesTheCustomersCurrentPhotoSavedAsAnOldDirectS3Url() {
        customer(21L, "https://jobhube.s3.eu-north-1.amazonaws.com/providers/21/images/" + FILE);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("image/jpeg").build(), new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));

        var photo = service.load(21L, FILE);

        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.bytes()).hasSize(3);
        verify(s3).getObjectAsBytes(GetObjectRequest.builder().bucket("jobhube").key("providers/21/images/" + FILE).build());
    }

    @Test
    void refusesImagesThatAreNotTheUsersProfilePhoto() {
        customer(21L, "https://jobhube.s3.eu-north-1.amazonaws.com/providers/21/images/other.jpg");
        assertThatThrownBy(() -> service.load(21L, FILE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.load(22L, FILE)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void refusesPathTricksAndNonImages() {
        customer(21L, "providers/21/images/" + FILE);
        assertThatThrownBy(() -> service.load(21L, "../documents/" + FILE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.load(21L, "a.pdf")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(s3);
    }

    private void customer(Long userId, String photo) {
        var c = new Customer();
        c.setUserId(userId);
        c.setProfilePhoto(photo);
        when(customers.findByUserId(userId)).thenReturn(Optional.of(c));
    }
}
