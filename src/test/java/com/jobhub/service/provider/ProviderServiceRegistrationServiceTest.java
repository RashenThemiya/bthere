package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceRegistrationServiceTest {
    @Mock ProviderTypeService types;
    @Mock ServiceOptionService options;
    @Mock ProviderDocumentService documents;
    @Mock ProviderQualificationService qualifications;
    @Mock ProviderAvailabilityService availability;
    @Mock ServiceCustomFieldService fields;
    @Mock ProviderOnboardingService onboarding;
    @InjectMocks ProviderServiceRegistrationService service;

    private ProviderServiceRegistrationRequest request(List<ProviderServiceRegistrationRequest.ServiceAreaGroup> areas) {
        return new ProviderServiceRegistrationRequest(
                new ProviderTypeAssignmentRequest(3L, BigDecimal.ONE),
                new UpdateProviderOptionsRequest(List.of(12L)),
                new UpdateProviderLanguagesRequest(List.of("ENGLISH")),
                new UpdateProviderSkillsRequest(List.of(10L)),
                List.of(), List.of(), List.of(),
                new ProviderScheduleRequest(true, true, List.of()), List.of(), areas,
                new ProviderCustomSubmissionRequest(List.of()));
    }

    private ProviderTypeAssignmentResponse assignment(String status) {
        return new ProviderTypeAssignmentResponse(14L, 3L, "Care", BigDecimal.ONE,
                "ACTIVE", "ACTIVE", "ACTIVE", status, false, null, null, List.of(), List.of());
    }

    private ProviderServiceRegistrationService transactional(PlatformTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (ProviderServiceRegistrationService) proxy.getProxy();
    }

    @Test
    void returnsFreshStatusAndCommitsCombinedRegistration() {
        var request = request(List.of());
        when(types.getAssignments(8L)).thenReturn(List.of(), List.of(assignment("APPROVED")));
        when(types.addAssignment(8L, request.service())).thenReturn(assignment("PENDING"));
        var manager = mock(PlatformTransactionManager.class);
        var transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);

        var response = transactional(manager).register(8L, request);

        assertThat(response.assignment().verificationStatus()).isEqualTo("APPROVED");
        var order = inOrder(types, options, qualifications, availability, fields, onboarding);
        order.verify(types).getAssignments(8L);
        order.verify(types).addAssignment(8L, request.service());
        order.verify(options).replaceProviderOptions(8L, 14L, request.options());
        order.verify(options).replaceLanguages(8L, 14L, request.languages());
        order.verify(qualifications).replaceSkills(8L, 14L, request.skills());
        order.verify(availability).replaceSchedule(8L, 14L, request.availability());
        order.verify(fields).saveSubmissions(8L, 14L, request.requirements());
        order.verify(types).getAssignments(8L);
        order.verify(onboarding).get(8L);
        verify(manager).commit(transaction);
        verify(manager, never()).rollback(any());
    }

    @Test
    void lateValidationFailureRollsBackWholeRegistration() {
        var request = request(List.of());
        when(types.getAssignments(8L)).thenReturn(List.of());
        when(types.addAssignment(8L, request.service())).thenReturn(assignment("PENDING"));
        when(fields.saveSubmissions(8L, 14L, request.requirements()))
                .thenThrow(new IllegalArgumentException("File belongs to another provider"));
        var manager = mock(PlatformTransactionManager.class);
        var transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);

        assertThatThrownBy(() -> transactional(manager).register(8L, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
        verifyNoInteractions(onboarding);
    }

    @Test
    void savesEverySuppliedSectionUnderTheNewAssignment() {
        var base = request(List.of());
        var document = mock(ProviderDocumentRequest.class);
        var certificate = mock(ProfessionalCertificateRequest.class);
        var education = mock(EducationQualificationRequest.class);
        var location = mock(ProviderServiceAreaRequest.class);
        var date = mock(ProviderUnavailableDateRequest.class);
        var group = new ProviderServiceRegistrationRequest.ServiceAreaGroup(
                12L, "PROVIDER_TO_CUSTOMER", List.of(location));
        var request = new ProviderServiceRegistrationRequest(base.service(), base.options(), base.languages(),
                base.skills(), List.of(document), List.of(certificate), List.of(education), null,
                List.of(date), List.of(group), base.requirements());
        when(types.getAssignments(8L)).thenReturn(List.of(), List.of(assignment("PENDING")));
        when(types.addAssignment(8L, request.service())).thenReturn(assignment("PENDING"));

        service.register(8L, request);

        verify(documents).addDocument(8L, document);
        verify(qualifications).addCertificate(8L, 14L, certificate);
        verify(qualifications).addEducation(8L, 14L, education);
        verify(availability).addUnavailableDate(8L, 14L, date);
        verify(availability).replaceServiceAreas(8L, 14L, 12L, "PROVIDER_TO_CUSTOMER", List.of(location));
        verify(availability, never()).replaceSchedule(anyLong(), anyLong(), any());
    }

    @Test
    void validatesNestedFieldsBeforeTheyReachTheServices() {
        var base = request(List.of());
        var invalid = new ProviderServiceRegistrationRequest(
                new ProviderTypeAssignmentRequest(null, BigDecimal.valueOf(-1)), base.options(),
                new UpdateProviderLanguagesRequest(List.of("INVALID")), base.skills(),
                base.documents(), base.certificates(), base.education(), null,
                base.unavailableDates(), base.serviceAreas(), base.requirements());
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(invalid))
                    .extracting(item -> item.getPropertyPath().toString())
                    .contains("service.providerTypeId", "service.experienceYears",
                            "languages.languages[0].<list element>");
        }
    }

    @Test
    void repeatedRegistrationDoesNotResetExistingApprovals() {
        when(types.getAssignments(8L)).thenReturn(List.of(assignment("APPROVED")));
        assertThatThrownBy(() -> service.register(8L, request(List.of())))
                .isInstanceOf(ConflictException.class);
        verify(types, never()).addAssignment(anyLong(), any());
        verifyNoInteractions(options, documents, qualifications, availability, fields);
    }

    @Test
    void rejectsLocationsForAnUnselectedOptionBeforeWriting() {
        when(types.getAssignments(8L)).thenReturn(List.of());
        var group = new ProviderServiceRegistrationRequest.ServiceAreaGroup(99L, "PROVIDER_TO_CUSTOMER", List.of());
        assertThatThrownBy(() -> service.register(8L, request(List.of(group))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be selected");
        verify(types, never()).addAssignment(anyLong(), any());
    }
}
