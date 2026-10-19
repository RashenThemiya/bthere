package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.entity.provider.ProviderServiceOption;
import com.jobhub.entity.provider.ServiceProviderDocument;
import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import com.jobhub.entity.provider.ServiceCustomField;
import com.jobhub.entity.provider.ProviderCustomFieldSubmission;
import com.jobhub.entity.provider.ServiceTypeDocumentRequirement;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceApprovalServiceTest {

    @Mock ServiceProviderTypeAssignmentRepository assignmentRepository;
    @Mock ServiceTypeDocumentRequirementRepository requirementRepository;
    @Mock ServiceProviderDocumentRepository documentRepository;
    @Mock ServiceProviderTypeRepository typeRepository;
    @Mock ServiceTypeSkillRepository skillRepository;
    @Mock ProviderServiceSkillRepository providerSkillRepository;
    @Mock ProviderProfessionalCertificateRepository certificateRepository;
    @Mock ProviderEducationQualificationRepository educationRepository;
    @Mock ServiceProviderAvailabilityRepository availabilityRepository;
    @Mock ServiceProviderServiceAreaRepository serviceAreaRepository;
    @Mock ProviderServiceOptionRepository providerOptionRepository;
    @Mock ServiceOptionRepository optionRepository;
    @Mock ServiceCustomFieldRepository customFieldRepository;
    @Mock ProviderCustomFieldSubmissionRepository customSubmissionRepository;
    @Mock ObjectMapper objectMapper;
    @Mock ProviderProfileFields profileFields;

    @InjectMocks ProviderServiceApprovalService service;

    @Test void requiredProfileFieldsUseLiveValuesInsteadOfStoredAnswers() {
        var assignment = new ServiceProviderTypeAssignment();
        assignment.setAssignmentId(7L); assignment.setServiceProviderId(10L); assignment.setServiceProviderTypeId(20L);
        var field = new ServiceCustomField(); field.setFieldId(1L); field.setCode("GENDER");
        field.setValueSource("PROVIDER_PROFILE"); field.setProfileField("gender"); field.setRequired(true);
        when(customFieldRepository.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                20L, "PROVIDER_REQUIREMENT", "ACTIVE")).thenReturn(List.of(field));
        when(profileFields.resolve(10L, field)).thenReturn(new ProviderProfileFields.Resolved(
                com.fasterxml.jackson.databind.node.TextNode.valueOf("FEMALE"), "PROVIDED"));
        assertThat(service.customRequirementsComplete(assignment)).isTrue();
        when(profileFields.resolve(10L, field)).thenReturn(new ProviderProfileFields.Resolved(null, "MISSING"));
        assertThat(service.customRequirementsComplete(assignment)).isFalse();
    }

    @Test void conditionalRequirementsReadProfileBackedParents() throws Exception {
        var assignment = new ServiceProviderTypeAssignment(); assignment.setServiceProviderId(10L);
        var parent = new ServiceCustomField(); parent.setFieldId(1L); parent.setCode("GENDER"); parent.setValueSource("PROVIDER_PROFILE");
        var child = new ServiceCustomField(); child.setConditionJson("{\"fieldCode\":\"GENDER\",\"value\":\"FEMALE\"}");
        when(objectMapper.readTree(child.getConditionJson())).thenReturn(new ObjectMapper().readTree(child.getConditionJson()));
        when(profileFields.resolve(10L, parent)).thenReturn(new ProviderProfileFields.Resolved(
                com.fasterxml.jackson.databind.node.TextNode.valueOf("FEMALE"), "PROVIDED"));
        assertThat(service.conditionApplies(assignment, child, java.util.Map.of("GENDER", parent), java.util.Map.of())).isTrue();
    }

    @Test
    void selectedOptionRequirementReusesApprovedProviderDocument() {
        ServiceProviderTypeAssignment assignment = new ServiceProviderTypeAssignment();
        assignment.setAssignmentId(7L);
        assignment.setServiceProviderId(10L);
        assignment.setServiceProviderTypeId(20L);

        ProviderServiceOption selected = new ProviderServiceOption();
        selected.setAssignmentId(7L);
        selected.setOptionId(30L);

        ServiceTypeDocumentRequirement common = requirement(20L, 1L, null);
        ServiceTypeDocumentRequirement optionSpecific = requirement(20L, 2L, 30L);
        ServiceTypeDocumentRequirement otherOption = requirement(20L, 3L, 31L);

        ServiceProviderDocument approved = new ServiceProviderDocument();
        approved.setServiceProviderId(10L);
        approved.setDocumentTypeId(2L);
        approved.setVerificationStatus("APPROVED");

        when(providerOptionRepository.findAllByAssignmentId(7L))
                .thenReturn(List.of(selected));
        when(requirementRepository.findAllByServiceProviderTypeId(20L))
                .thenReturn(List.of(common, optionSpecific, otherOption));
        when(documentRepository.findAllByServiceProviderIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(approved));

        assertThat(service.requiredDocumentTypeIds(assignment))
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(service.satisfiedDocumentTypeIds(10L, assignment))
                .containsExactly(2L);
    }

    @Test
    void providerScopedRequirementReusesApprovedAnswerFromAnotherService() {
        ServiceProviderTypeAssignment assignment = new ServiceProviderTypeAssignment();
        assignment.setAssignmentId(8L);
        assignment.setServiceProviderId(10L);
        assignment.setServiceProviderTypeId(21L);

        ServiceCustomField field = new ServiceCustomField();
        field.setFieldId(50L);
        field.setServiceProviderTypeId(21L);
        field.setScope("PROVIDER_REQUIREMENT");
        field.setReuseScope("PROVIDER");
        field.setCode("DRIVING_EXPERIENCE");
        field.setFieldType("NUMBER");
        field.setRequired(true);
        field.setRequiresApproval(true);
        field.setStatus("ACTIVE");

        ProviderCustomFieldSubmission reused = new ProviderCustomFieldSubmission();
        reused.setAssignmentId(2L);
        reused.setProviderId(10L);
        reused.setServiceProviderTypeId(11L);
        reused.setReusableCode("DRIVING_EXPERIENCE");
        reused.setReusableFieldType("NUMBER");
        reused.setVerificationStatus("APPROVED");

        when(customFieldRepository
                .findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                        21L, "PROVIDER_REQUIREMENT", "ACTIVE"))
                .thenReturn(List.of(field));
        when(providerOptionRepository.findAllByAssignmentId(8L)).thenReturn(List.of());
        when(customSubmissionRepository.findAllByAssignmentId(8L)).thenReturn(List.of());
        when(customSubmissionRepository
                .findAllByProviderIdAndReusableCodeAndVerificationStatusOrderByUpdatedAtDesc(
                        10L, "DRIVING_EXPERIENCE", "APPROVED"))
                .thenReturn(List.of(reused));

        assertThat(service.customRequirementsComplete(assignment)).isTrue();
    }

    @Test
    void serviceScopedRequirementDoesNotReuseAnswerFromAnotherService() {
        ServiceProviderTypeAssignment assignment = new ServiceProviderTypeAssignment();
        assignment.setAssignmentId(9L);
        assignment.setServiceProviderId(10L);
        assignment.setServiceProviderTypeId(21L);

        ServiceCustomField field = new ServiceCustomField();
        field.setFieldId(51L);
        field.setServiceProviderTypeId(21L);
        field.setScope("PROVIDER_REQUIREMENT");
        field.setReuseScope("SERVICE");
        field.setCode("EQUIPMENT_AVAILABLE");
        field.setFieldType("BOOLEAN");
        field.setRequired(true);
        field.setRequiresApproval(true);
        field.setStatus("ACTIVE");

        ProviderCustomFieldSubmission otherService = new ProviderCustomFieldSubmission();
        otherService.setProviderId(10L);
        otherService.setServiceProviderTypeId(22L);
        otherService.setReusableCode("EQUIPMENT_AVAILABLE");
        otherService.setReusableFieldType("BOOLEAN");
        otherService.setVerificationStatus("APPROVED");

        when(customFieldRepository
                .findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                        21L, "PROVIDER_REQUIREMENT", "ACTIVE"))
                .thenReturn(List.of(field));
        when(providerOptionRepository.findAllByAssignmentId(9L)).thenReturn(List.of());
        when(customSubmissionRepository.findAllByAssignmentId(9L)).thenReturn(List.of());
        when(customSubmissionRepository
                .findAllByProviderIdAndReusableCodeAndVerificationStatusOrderByUpdatedAtDesc(
                        10L, "EQUIPMENT_AVAILABLE", "APPROVED"))
                .thenReturn(List.of(otherService));

        assertThat(service.customRequirementsComplete(assignment)).isFalse();
    }

    private ServiceTypeDocumentRequirement requirement(
            Long serviceTypeId, Long documentTypeId, Long optionId) {
        ServiceTypeDocumentRequirement requirement = new ServiceTypeDocumentRequirement();
        requirement.setServiceProviderTypeId(serviceTypeId);
        requirement.setDocumentTypeId(documentTypeId);
        requirement.setOptionId(optionId);
        requirement.setMandatory(true);
        requirement.setRequiresApproval(true);
        return requirement;
    }
}
