package com.jobhub.service.upload;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class S3CustomerDocumentsTest {
    @Test void publishedRasterImagesReceiveTenMinuteSignedUrls() throws Exception {
        var signer = mock(S3Presigner.class);
        var published = new S3PrivateDocumentService(s3, signer);
        ReflectionTestUtils.setField(published, "bucket", "test-private");
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png").contentLength(100L).build());
        var signed = mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(new java.net.URI("https://example.com/signed").toURL());
        when(signer.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(signed);
        var result = published.createPublishedProviderImageUrl(25L, "providers/25/images/photo.png");
        assertThat(result.url()).isEqualTo("https://example.com/signed");
        verify(signer).presignGetObject(argThat((software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest r) ->
                r.signatureDuration().equals(java.time.Duration.ofMinutes(10))
                && r.getObjectRequest().key().equals("providers/25/images/photo.png")));
    }

    @Test void publicFileLinksRequireOwnedSupportedExistingObjects() throws Exception {
        var signer = mock(S3Presigner.class);
        var published = new S3PrivateDocumentService(s3, signer);
        ReflectionTestUtils.setField(published, "bucket", "test-private");
        for (String key : java.util.List.of("providers/99/documents/a.pdf", "customers/25/documents/a.pdf",
                "providers/25/documents/../a.pdf", "providers/25/documents/a.html")) {
            assertThatThrownBy(() -> published.createPublishedProviderFileUrl(25L, key))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(s3, signer);
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("application/pdf").contentLength(100L).build());
        var signed = mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(new java.net.URI("https://example.com/signed-pdf").toURL());
        when(signer.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(signed);
        var result = published.createPublishedProviderFileUrl(25L, "providers/25/documents/a.pdf");
        assertThat(result.key()).isEqualTo("providers/25/documents/a.pdf");
        assertThat(result.url()).isEqualTo("https://example.com/signed-pdf");
        clearInvocations(signer);
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("text/html").contentLength(100L).build());
        assertThatThrownBy(() -> published.createPublishedProviderFileUrl(25L, "providers/25/documents/a.pdf"))
                .hasMessageContaining("must be PDF");
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(() -> published.createPublishedProviderFileUrl(25L, "providers/25/documents/a.pdf"))
                .hasMessageContaining("does not exist");
        verifyNoInteractions(signer);
    }

    @Test void publicationRejectsForeignUnsafeAndNonImageObjectsBeforeSigning() {
        ReflectionTestUtils.setField(service, "bucket", "test-private");
        assertThatThrownBy(() -> service.createPublishedProviderImageUrl(25L, "providers/99/images/a.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createPublishedProviderImageUrl(25L, "providers/25/images/../a.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createPublishedProviderImageUrl(25L, "providers/25/documents/a.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3);
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("application/pdf").contentLength(100L).build());
        assertThatThrownBy(() -> service.createPublishedProviderImageUrl(25L, "providers/25/images/a.jpg"))
                .hasMessageContaining("must be a JPG");
    }

    @Test void providerPartsInspectStoredMetadataAndRejectForeignKeys() {
        ReflectionTestUtils.setField(service, "bucket", "test-private");
        assertThatThrownBy(()->service.inspectProviderFile(25L,"providers/99/documents/front.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.inspectProviderFile(25L,"providers/25/documents/../front.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3);
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/jpeg").contentLength(100L).build());
        assertThat(service.inspectProviderFile(25L,"providers/25/documents/front.jpg").size()).isEqualTo(100);
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(()->service.inspectProviderFile(25L,"providers/25/documents/missing.jpg"))
                .hasMessageContaining("does not exist");
    }

    S3Client s3 = mock(S3Client.class);
    S3PrivateDocumentService service = new S3PrivateDocumentService(s3, mock(S3Presigner.class));

    @Test void customerUploadsUsePrivateCustomerNamespace() {
        ReflectionTestUtils.setField(service, "bucket", "test-private");
        var result = service.uploadCustomer(25L, new MockMultipartFile("file", "nic.pdf", "application/pdf", new byte[]{1}));
        assertThat(result.key()).startsWith("customers/25/documents/");
        assertThat(result.url()).startsWith("s3://test-private/customers/25/documents/");
    }

    @Test void rejectsAnotherCustomerAndProviderKeysBeforeS3Access() {
        assertThatThrownBy(() -> service.inspectCustomerFile(25L, "customers/99/documents/nic.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.inspectCustomerFile(25L, "providers/25/documents/nic.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.inspectCustomerFile(25L, "customers/25/documents/../nic.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3);
    }

    @Test void readsActualUploadedFileMetadata() {
        ReflectionTestUtils.setField(service, "bucket", "test-private");
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/jpeg").contentLength(100L).build());
        assertThat(service.inspectCustomerFile(25L, "customers/25/documents/nic.jpg").size()).isEqualTo(100);
    }
}
