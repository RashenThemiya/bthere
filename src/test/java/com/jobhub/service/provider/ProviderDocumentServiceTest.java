package com.jobhub.service.provider;

import com.jobhub.dto.provider.ProviderDocumentRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderDocumentServiceTest {
    private final ProviderProfileService profiles = mock(ProviderProfileService.class);
    private final ServiceProviderRepository providers = mock(ServiceProviderRepository.class);
    private final DocumentTypeRepository types = mock(DocumentTypeRepository.class);
    private final ServiceProviderDocumentRepository documents = mock(ServiceProviderDocumentRepository.class);
    private final ProviderServiceApprovalService approvals = mock(ProviderServiceApprovalService.class);
    private final ProviderDocumentService service = new ProviderDocumentService(profiles, providers, types, documents, approvals);
    private final DocumentType type = new DocumentType();

    @BeforeEach void setup() {
        var provider = new ServiceProvider();
        provider.setServiceProviderId(10L);
        when(profiles.findByUserId(25L)).thenReturn(provider);
        type.setDocumentTypeId(1L);
        type.setName("NIC");
        type.setStatus("ACTIVE");
        when(types.findById(1L)).thenReturn(Optional.of(type));
        when(documents.findByServiceProviderIdAndDocumentTypeId(10L, 1L)).thenReturn(Optional.empty());
        when(documents.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ProviderDocumentRequest request(LocalDate issued, LocalDate expiry) {
        return new ProviderDocumentRequest(1L, null, null, "providers/25/documents/nic.jpg", issued, expiry);
    }

    @Test void uploadOnlyUsesTypeNameAndOptionalMetadata() {
        var result = service.addDocument(25L, request(null, null));
        assertThat(result.documentName()).isEqualTo("NIC");
        assertThat(result.issuedDate()).isNull();
        assertThat(result.expiryDate()).isNull();
    }

    @Test void adminCanRequireIssuedDate() {
        type.setIssuedDateMode("REQUIRED");
        assertThatThrownBy(() -> service.addDocument(25L, request(null, null)))
                .hasMessageContaining("Issued date is required");
    }

    @Test void explicitOptionalExpiryOverridesLegacyFlag() {
        type.setHasExpiry(true);
        type.setExpiryDateMode("OPTIONAL");
        assertThatCode(() -> service.addDocument(25L, request(null, null))).doesNotThrowAnyException();
    }

    @Test void legacyExpiryRequirementRemainsEnforced() {
        type.setHasExpiry(true);
        assertThatThrownBy(() -> service.addDocument(25L, request(null, null)))
                .hasMessageContaining("Expiry date is required");
    }

    @Test void hiddenDateCannotBeSubmitted() {
        type.setIssuedDateMode("HIDDEN");
        assertThatThrownBy(() -> service.addDocument(25L, request(LocalDate.of(2020, 1, 1), null)))
                .hasMessageContaining("Issued date is disabled");
    }

    @Test void expiryCannotPrecedeIssueDate() {
        assertThatThrownBy(() -> service.addDocument(25L,
                request(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1))))
                .hasMessageContaining("Expiry date cannot be before issued date");
    }
}
