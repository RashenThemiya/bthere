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
