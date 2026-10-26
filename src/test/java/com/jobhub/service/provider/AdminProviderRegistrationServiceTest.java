package com.jobhub.service.provider;

import com.jobhub.dto.admin.AdminProviderRegistrationApi.*;
import com.jobhub.dto.provider.*;
import com.jobhub.dto.upload.FileUploadResponse;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.upload.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminProviderRegistrationServiceTest {
    @Mock ServiceProviderRepository providers;
    @Mock ServiceProviderTypeAssignmentRepository assignments;
    @Mock ProviderServiceRegistrationService registrations;
    @Mock ProviderTypeService types;
    @Mock ProviderOnboardingService onboarding;
    @Mock ProviderDocumentService documents;
    @Mock ServiceCustomFieldService fields;
    @Mock ProviderQualificationService qualifications;
    @Mock ServiceOptionService options;
    @Mock ProviderAvailabilityService availability;
    @Mock S3PrivateDocumentService storage;
    @Mock S3ImageUploadService images;
    @Mock AuditService audit;
    @InjectMocks AdminProviderRegistrationService service;
    ServiceProvider provider;

    @BeforeEach void setup() {
        provider = new ServiceProvider(); provider.setServiceProviderId(15L); provider.setUserId(25L);
    }
    @Test void registerUsesTargetUserAndRecordsActualAdminWithoutGrantingOverride() {
        when(providers.lockById(15L)).thenReturn(Optional.of(provider));
        var input = mock(ProviderServiceRegistrationRequest.class);
        var assignment = mock(ProviderTypeAssignmentResponse.class);
        when(assignment.assignmentId()).thenReturn(14L);
        var result = new ProviderServiceRegistrationResponse(assignment, null);
        when(registrations.register(25L, input)).thenReturn(result);
        assertThat(service.register(99L, 15L, new Registration(input, Source.WHATSAPP, " Reviewed photos ")))
                .isSameAs(result);
        verify(audit).record(eq(99L), eq("ADMIN_PROVIDER_SERVICE_REGISTRATION"), eq("PROVIDER_SERVICE_ASSIGNMENT"),
                eq(14L), isNull(), eq(Map.of("providerId", 15L, "source", Source.WHATSAPP, "note", "Reviewed photos")));
        verifyNoInteractions(types, qualifications, fields);
    }
    @Test void duplicateOrInvalidRegistrationIsNotRecordedAsSuccessful() {
        when(providers.lockById(15L)).thenReturn(Optional.of(provider));
        var input = mock(ProviderServiceRegistrationRequest.class);
        when(registrations.register(25L, input)).thenThrow(new ConflictException("Already registered"));
        assertThatThrownBy(() -> service.register(99L, 15L, new Registration(input, Source.PHONE, null)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(audit);
    }
    @Test void uploadsUseProviderNamespaceRatherThanAdminNamespace() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var file = new MockMultipartFile("file", "nic.pdf", "application/pdf", new byte[]{1});
        var uploaded = new FileUploadResponse("providers/25/documents/nic.pdf", "s3://bucket/key", "application/pdf", 1);
        when(storage.upload(25L, file)).thenReturn(uploaded);
        assertThat(service.upload(99L, 15L, file, false, Source.WHATSAPP)).isSameAs(uploaded);
        verify(audit).record(eq(99L), eq("ADMIN_PROVIDER_FILE_UPLOAD"), eq("SERVICE_PROVIDER"), eq(15L),
                isNull(), eq(Map.of("key", uploaded.key(), "source", Source.WHATSAPP)));
        var image = new FileUploadResponse("providers/25/images/photo.jpg", "https://example.com/photo", "image/jpeg", 1);
        when(images.uploadImage("providers/25/images", file)).thenReturn(image);
        assertThat(service.upload(99L, 15L, file, true, Source.IN_PERSON)).isSameAs(image);
        verify(storage, never()).upload(eq(99L), any());
    }
    @Test void documentSubmissionUsesExistingValidationAndLeavesReviewSeparate() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var input = new ProviderDocumentRequest(1L, "NIC", null, "providers/25/documents/nic.pdf", null, null);
        var result = mock(ProviderDocumentResponse.class);
        when(result.documentId()).thenReturn(44L);
        when(documents.addDocument(25L, input)).thenReturn(result);
        assertThat(service.addDocument(99L, 15L, new Document(input, Source.WHATSAPP, null))).isSameAs(result);
        verify(documents, never()).review(any(), any(), any());
        verify(audit).record(eq(99L), eq("ADMIN_PROVIDER_DOCUMENT_SUBMITTED"), eq("PROVIDER_DOCUMENT"),
                eq(44L), isNull(), eq(Map.of("providerId", 15L, "source", Source.WHATSAPP, "note", "")));
    }
    @Test void invalidDocumentPropagatesWithoutAuditOrApproval() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var input = new ProviderDocumentRequest(1L, "NIC", null, "providers/99/documents/nic.pdf", null, null);
        when(documents.addDocument(25L, input)).thenThrow(new IllegalArgumentException("Not owned"));
        assertThatThrownBy(() -> service.addDocument(99L, 15L, new Document(input, Source.WHATSAPP, null)))
                .hasMessage("Not owned");
        verifyNoInteractions(audit);
    }
    @Test void anotherProvidersAssignmentCannotBeEdited() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var input = new ProviderCustomSubmissionRequest(List.of());
        assertThatThrownBy(() -> service.requirements(99L, 15L, 88L, input)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.certificate(99L, 15L, 88L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.education(99L, 15L, 88L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.options(99L, 15L, 88L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.languages(99L, 15L, 88L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.skills(99L, 15L, 88L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.schedule(99L, 15L, 88L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.areas(99L, 15L, 88L, 12L, "ONLINE", List.of())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(fields, qualifications, options, availability, audit);
    }
    @Test void ownedRequirementsAreSubmittedAsProviderAndAuditedAsAdmin() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        when(assignments.findByAssignmentIdAndServiceProviderId(14L, 15L))
                .thenReturn(Optional.of(new ServiceProviderTypeAssignment()));
        var input = new ProviderCustomSubmissionRequest(List.of());
        when(fields.saveSubmissions(25L, 14L, input)).thenReturn(List.of());
        assertThat(service.requirements(99L, 15L, 14L, input)).isEmpty();
        verify(audit).record(eq(99L), eq("ADMIN_PROVIDER_REQUIREMENTS_SUBMITTED"), eq("PROVIDER_SERVICE_ASSIGNMENT"),
                eq(14L), isNull(), eq(Map.of("providerId", 15L)));
    }
    @Test void registrationDetailsCanBeCompletedByAdminUsingProviderValidation() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        when(assignments.findByAssignmentIdAndServiceProviderId(14L, 15L))
                .thenReturn(Optional.of(new ServiceProviderTypeAssignment()));
        var optionRequest = new UpdateProviderOptionsRequest(List.of(12L));
        var languages = new UpdateProviderLanguagesRequest(List.of("ENGLISH"));
        var skills = new UpdateProviderSkillsRequest(List.of());
        var schedule = new ProviderScheduleRequest(true, true, List.of());
        service.options(99L, 15L, 14L, optionRequest);
        service.languages(99L, 15L, 14L, languages);
        service.skills(99L, 15L, 14L, skills);
        service.schedule(99L, 15L, 14L, schedule);
        service.areas(99L, 15L, 14L, 12L, "PROVIDER_TO_CUSTOMER", List.of());
        verify(options).replaceProviderOptions(25L, 14L, optionRequest);
        verify(options).replaceLanguages(25L, 14L, languages);
        verify(qualifications).replaceSkills(25L, 14L, skills);
        verify(availability).replaceSchedule(25L, 14L, schedule);
        verify(availability).replaceServiceAreas(25L, 14L, 12L, "PROVIDER_TO_CUSTOMER", List.of());
        verify(audit, times(5)).record(eq(99L), anyString(), eq("PROVIDER_SERVICE_ASSIGNMENT"), eq(14L), isNull(),
                eq(Map.of("providerId", 15L)));
    }

    @Test void missingProviderCannotUploadOrRegister() {
        var file = new MockMultipartFile("file", new byte[]{1});
        assertThatThrownBy(() -> service.upload(99L, 15L, file, false, Source.WHATSAPP))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.register(99L, 15L, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage, images, registrations, audit);
    }
    @Test void contextResolvesProviderAccount() {
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var context = service.context(15L);
        assertThat(context.providerId()).isEqualTo(15L);
        verify(types).getAssignments(25L);
        verify(onboarding).get(25L);
        verify(documents).getProviderDocuments(25L);
    }
}
