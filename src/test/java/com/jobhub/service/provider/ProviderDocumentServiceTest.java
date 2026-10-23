package com.jobhub.service.provider;

import com.jobhub.dto.provider.ProviderDocumentRequest;
import com.jobhub.dto.provider.DocumentPartDefinition;
import com.jobhub.dto.provider.DocumentPartFiles;
import com.jobhub.dto.provider.CreateDocumentTypeRequest;
import java.util.List;
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
    private final com.jobhub.service.upload.S3PrivateDocumentService storage = mock(com.jobhub.service.upload.S3PrivateDocumentService.class);
    private final DocumentPartService parts = new DocumentPartService(storage);
    private final ProviderDocumentService service = new ProviderDocumentService(profiles, providers, types, documents, approvals, parts);
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

    private void configureParts() {
        type.setPartsJson(DocumentParts.write(List.of(
                new DocumentPartDefinition("FRONT","Front image",true,1,5,List.of("image/jpeg")),
                new DocumentPartDefinition("BACK","Back image",true,1,5,List.of("image/jpeg")),
                new DocumentPartDefinition("EXTRA","Additional pages",false,3,5,List.of("application/pdf")))));
    }

    private ProviderDocumentRequest bundle(String front, String back) {
        return new ProviderDocumentRequest(1L,null,null,null,null,null,List.of(
                new DocumentPartFiles("FRONT",List.of(front)),new DocumentPartFiles("BACK",List.of(back))));
    }

    @Test void savesCompleteBundleWithSchemaSnapshotAndResetsReviewOnReplacement() {
        configureParts();
        var existing=new ServiceProviderDocument(); existing.setDocumentId(20L);
        existing.setVerificationStatus("APPROVED"); existing.setVerifiedBy(7L);
        when(documents.findByServiceProviderIdAndDocumentTypeId(10L,1L)).thenReturn(Optional.of(existing));
        when(storage.inspectProviderFile(eq(25L),anyString())).thenReturn(
                new com.jobhub.service.upload.S3PrivateDocumentService.CustomerFile("image/jpeg",100L));
        var result=service.addDocument(25L,bundle("providers/25/documents/front.jpg","providers/25/documents/back.jpg"));
        assertThat(result.documentId()).isEqualTo(20L);
        assertThat(result.parts()).hasSize(2);
        assertThat(result.partDefinitions()).hasSize(3);
        assertThat(result.documentKey()).isEqualTo("providers/25/documents/front.jpg");
        assertThat(result.verificationStatus()).isEqualTo("PENDING");
        assertThat(result.verifiedBy()).isNull();
        type.setPartsJson("[]");
        assertThat(DocumentParts.definitions(existing.getPartsDefinitionJson())).hasSize(3);
        verify(approvals).refreshProviderApprovals(10L);
    }

    @Test void missingRequiredPartDoesNotOverwriteApprovedEvidence() {
        configureParts();
        when(storage.inspectProviderFile(eq(25L),anyString())).thenReturn(
                new com.jobhub.service.upload.S3PrivateDocumentService.CustomerFile("image/jpeg",100L));
        var incomplete=new ProviderDocumentRequest(1L,null,null,null,null,null,
                List.of(new DocumentPartFiles("FRONT",List.of("providers/25/documents/front.jpg"))));
        assertThatThrownBy(()->service.addDocument(25L,incomplete)).hasMessageContaining("required document parts");
        verify(documents,never()).save(any()); verify(providers,never()).save(any());
        verifyNoInteractions(approvals);
    }

    @Test void duplicateUnknownAndForeignPartsAreRejected() {
        configureParts();
        assertThatThrownBy(()->service.addDocument(25L,bundle("providers/99/documents/front.jpg","providers/25/documents/back.jpg")))
                .hasMessageContaining("does not belong");
        verifyNoInteractions(storage);
        var unknown=new ProviderDocumentRequest(1L,null,null,null,null,null,
                List.of(new DocumentPartFiles("OTHER",List.of("providers/25/documents/front.jpg"))));
        assertThatThrownBy(()->service.addDocument(25L,unknown)).hasMessageContaining("Unknown or duplicate");
        when(storage.inspectProviderFile(eq(25L),anyString())).thenReturn(
                new com.jobhub.service.upload.S3PrivateDocumentService.CustomerFile("image/jpeg",100L));
        assertThatThrownBy(()->service.addDocument(25L,bundle("providers/25/documents/same.jpg","providers/25/documents/same.jpg")))
                .hasMessageContaining("multiple document parts");
        verify(documents,never()).save(any());
    }

    @Test void countsAndActualStoredFileLimitsAreEnforced() {
        configureParts();
        var tooMany=new ProviderDocumentRequest(1L,null,null,null,null,null,List.of(
                new DocumentPartFiles("FRONT",List.of("providers/25/documents/a.jpg","providers/25/documents/b.jpg"))));
        assertThatThrownBy(()->service.addDocument(25L,tooMany)).hasMessageContaining("file count");
        when(storage.inspectProviderFile(eq(25L),anyString())).thenReturn(
                new com.jobhub.service.upload.S3PrivateDocumentService.CustomerFile("application/pdf",100L));
        assertThatThrownBy(()->service.addDocument(25L,bundle("providers/25/documents/a.pdf","providers/25/documents/b.pdf")))
                .hasMessageContaining("file type or size");
        when(storage.inspectProviderFile(eq(25L),anyString())).thenReturn(
                new com.jobhub.service.upload.S3PrivateDocumentService.CustomerFile("image/jpeg",6L*1024*1024));
        assertThatThrownBy(()->service.addDocument(25L,bundle("providers/25/documents/a.jpg","providers/25/documents/b.jpg")))
                .hasMessageContaining("file type or size");
        verify(documents,never()).save(any());
    }

    @Test void legacyAdminUpdatePreservesPartsAndApprovalsButExplicitEmptyClearsConfigurationOnly() {
        configureParts();
        when(types.save(any())).thenAnswer(i->i.getArgument(0));
        var result=service.updateDocumentType(1L,new CreateDocumentTypeRequest("NIC",null,false,null,null));
        assertThat(result.parts()).hasSize(3);
        result=service.updateDocumentType(1L,new CreateDocumentTypeRequest("NIC",null,false,null,null,List.of()));
        assertThat(result.parts()).isEmpty();
        verifyNoInteractions(documents,approvals);
    }

    @Test void invalidAdminPartDefinitionsAndSingleKeyForMultipartAreRejected() {
        configureParts();
        var definition=type.effectiveParts().get(0);
        assertThatThrownBy(()->service.updateDocumentType(1L,new CreateDocumentTypeRequest("NIC",null,false,null,null,List.of(definition,definition))))
                .hasMessageContaining("duplicate code");
        assertThatThrownBy(()->service.addDocument(25L,request(null,null))).hasMessageContaining("Use parts");
        verify(documents,never()).save(any());
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
