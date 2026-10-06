package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProviderServiceApprovalService {

    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ServiceTypeDocumentRequirementRepository requirementRepository;
    private final ServiceProviderDocumentRepository documentRepository;
    private final ServiceProviderTypeRepository typeRepository;
    private final ServiceTypeSkillRepository skillRepository;
    private final ProviderServiceSkillRepository providerSkillRepository;
    private final ProviderProfessionalCertificateRepository certificateRepository;
    private final ProviderEducationQualificationRepository educationRepository;
    private final ServiceProviderAvailabilityRepository availabilityRepository;
    private final ServiceProviderServiceAreaRepository serviceAreaRepository;
    private final ProviderServiceOptionRepository providerOptionRepository;
    private final ServiceOptionRepository optionRepository;
    private final ServiceCustomFieldRepository customFieldRepository;
    private final ProviderCustomFieldSubmissionRepository customSubmissionRepository;
    private final ObjectMapper objectMapper;

    public void refreshProviderApprovals(Long providerId) {
        List<ServiceProviderTypeAssignment> assignments =
                assignmentRepository.findAllByServiceProviderId(providerId);

        for (ServiceProviderTypeAssignment assignment : assignments) {
            if (assignment.isEmergencyOverride()
                    && (assignment.getEmergencyOverrideExpiresAt() == null
                    || assignment.getEmergencyOverrideExpiresAt().isAfter(java.time.LocalDateTime.now()))) {
                assignment.setVerificationStatus("APPROVED");
                continue;
            }
            if (assignment.isEmergencyOverride()) {
                assignment.setEmergencyOverride(false);
            }
            Set<Long> required = requiredDocumentTypeIds(
                    assignment.getServiceProviderTypeId()
            );
            Set<Long> satisfied = satisfiedDocumentTypeIds(
                    providerId,
                    assignment.getServiceProviderTypeId()
            );
            boolean complete = satisfied.containsAll(required)
                    && skillsComplete(assignment)
                    && certificatesComplete(assignment)
                    && educationComplete(assignment)
                    && availabilityComplete(assignment)
                    && serviceAreasComplete(assignment)
                    && optionsComplete(assignment)
                    && customRequirementsComplete(assignment);
            assignment.setVerificationStatus(complete ? "APPROVED" : "PENDING");
        }
        assignmentRepository.saveAll(assignments);
    }

    public Set<Long> requiredDocumentTypeIds(Long providerTypeId) {
        return requirementRepository.findAllByServiceProviderTypeId(providerTypeId)
                .stream()
                .filter(ServiceTypeDocumentRequirement::isMandatory)
                .map(ServiceTypeDocumentRequirement::getDocumentTypeId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public Set<Long> approvedDocumentTypeIds(Long providerId) {
        Set<Long> result = new HashSet<>();
        documentRepository.findAllByServiceProviderIdAndVerificationStatus(
                        providerId,
                        "APPROVED"
                )
                .stream()
                .filter(document -> document.getExpiryDate() == null
                        || !document.getExpiryDate().isBefore(LocalDate.now()))
                .map(ServiceProviderDocument::getDocumentTypeId)
                .forEach(result::add);
        return result;
    }

    public Set<Long> satisfiedDocumentTypeIds(Long providerId, Long providerTypeId) {
        List<ServiceProviderDocument> documents = documentRepository
                .findAllByServiceProviderIdOrderByCreatedAtDesc(providerId);
        Set<Long> result = new HashSet<>();
        for (ServiceTypeDocumentRequirement requirement : requirementRepository
                .findAllByServiceProviderTypeId(providerTypeId)) {
            if (!requirement.isMandatory()) {
                continue;
            }
            documents.stream()
                    .filter(document -> document.getDocumentTypeId()
                            .equals(requirement.getDocumentTypeId()))
                    .filter(document -> document.getExpiryDate() == null
                            || !document.getExpiryDate().isBefore(LocalDate.now()))
                    .filter(document -> !requirement.isRequiresApproval()
                            || "APPROVED".equals(document.getVerificationStatus()))
                    .findFirst()
                    .ifPresent(document -> result.add(document.getDocumentTypeId()));
        }
        return result;
    }

    public boolean documentsComplete(ServiceProviderTypeAssignment assignment) {
        return satisfiedDocumentTypeIds(
                assignment.getServiceProviderId(), assignment.getServiceProviderTypeId())
                .containsAll(requiredDocumentTypeIds(assignment.getServiceProviderTypeId()));
    }

    public boolean skillsComplete(ServiceProviderTypeAssignment assignment) {
        List<ServiceTypeSkill> required = skillRepository
                .findAllByServiceProviderTypeIdOrderByNameAsc(assignment.getServiceProviderTypeId())
                .stream().filter(ServiceTypeSkill::isMandatory).toList();
        List<ProviderServiceSkill> selected = providerSkillRepository
                .findAllByAssignmentId(assignment.getAssignmentId());
        return required.stream().allMatch(skill -> selected.stream().anyMatch(item ->
                item.getSkillId().equals(skill.getSkillId())
                        && (!skill.isRequiresApproval()
                        || "APPROVED".equals(item.getVerificationStatus()))));
    }

    public boolean certificatesComplete(ServiceProviderTypeAssignment assignment) {
        ServiceProviderType type = typeRepository.findById(assignment.getServiceProviderTypeId())
                .orElse(null);
        if (type == null || !"REQUIRED".equals(type.getCertificateRequirement())) {
            return true;
        }
        return certificateRepository.findAllByAssignmentIdOrderByCreatedAtDesc(
                        assignment.getAssignmentId()).stream()
                .filter(item -> item.getExpiryDate() == null
                        || !item.getExpiryDate().isBefore(LocalDate.now()))
                .anyMatch(item -> !type.isCertificateRequiresApproval()
                        || "APPROVED".equals(item.getVerificationStatus()));
    }

    public boolean educationComplete(ServiceProviderTypeAssignment assignment) {
        ServiceProviderType type = typeRepository.findById(assignment.getServiceProviderTypeId())
                .orElse(null);
        if (type == null || !"REQUIRED".equals(type.getEducationRequirement())) {
            return true;
        }
        return educationRepository.findAllByAssignmentIdOrderByCreatedAtDesc(
                        assignment.getAssignmentId()).stream()
                .anyMatch(item -> !type.isEducationRequiresApproval()
                        || "APPROVED".equals(item.getVerificationStatus()));
    }

    public boolean availabilityComplete(ServiceProviderTypeAssignment assignment) {
        ServiceProviderType type = typeRepository.findById(assignment.getServiceProviderTypeId())
                .orElse(null);
        if (type == null || !type.isAvailabilityEnabled()) {
            return true;
        }
        return assignment.isAvailableAnyDay()
                || !availabilityRepository
                .findAllByAssignmentIdAndScheduleTypeOrderByDayOfWeekAscStartTimeAsc(
                        assignment.getAssignmentId(), "WEEKLY").isEmpty();
    }

    public boolean serviceAreasComplete(ServiceProviderTypeAssignment assignment) {
        Set<Long> selectedOptionIds = providerOptionRepository
                .findAllByAssignmentId(assignment.getAssignmentId()).stream()
                .map(ProviderServiceOption::getOptionId)
                .collect(java.util.stream.Collectors.toSet());
        if (selectedOptionIds.isEmpty()) return true;

        return optionRepository.findAllByOptionIdInAndServiceProviderTypeIdAndStatus(
                        selectedOptionIds, assignment.getServiceProviderTypeId(), "ACTIVE")
                .stream()
                .filter(ServiceOption::isLocationEnabled)
                .allMatch(option -> option.getDeliveryModes().stream()
                        .filter(mode -> !"ONLINE".equals(mode))
                        .allMatch(mode -> serviceAreaRepository
                                .existsByAssignmentIdAndOptionIdAndDeliveryMode(
                                        assignment.getAssignmentId(), option.getOptionId(), mode)));
    }

    public boolean optionsComplete(ServiceProviderTypeAssignment assignment) {
        ServiceProviderType type = typeRepository.findById(assignment.getServiceProviderTypeId())
                .orElse(null);
        if (type == null || !type.isOptionsRequired()) {
            return true;
        }
        int minimum = type.getMinimumOptionSelections() == null
                ? 1 : type.getMinimumOptionSelections();
        long approved = providerOptionRepository.findAllByAssignmentId(assignment.getAssignmentId())
                .stream().filter(item -> "APPROVED".equals(item.getVerificationStatus())).count();
        return approved >= minimum;
    }

    public boolean customRequirementsComplete(ServiceProviderTypeAssignment assignment) {
        List<ServiceCustomField> fields = customFieldRepository
                .findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                        assignment.getServiceProviderTypeId(), "PROVIDER_REQUIREMENT", "ACTIVE");
        Set<Long> selectedOptions = providerOptionRepository
                .findAllByAssignmentId(assignment.getAssignmentId()).stream()
                .map(ProviderServiceOption::getOptionId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, ProviderCustomFieldSubmission> submissions = new java.util.HashMap<>();
        customSubmissionRepository.findAllByAssignmentId(assignment.getAssignmentId())
                .forEach(item -> submissions.put(item.getFieldId(), item));
        Map<String, ServiceCustomField> byCode = new java.util.HashMap<>();
        fields.forEach(item -> byCode.put(item.getCode(), item));

        return fields.stream()
                .filter(ServiceCustomField::isRequired)
                .filter(field -> field.getOptionId() == null
                        || selectedOptions.contains(field.getOptionId()))
                .filter(field -> conditionApplies(field, byCode, submissions))
                .allMatch(field -> {
                    ProviderCustomFieldSubmission submission = submissions.get(field.getFieldId());
                    return submission != null && (!field.isRequiresApproval()
                            || "APPROVED".equals(submission.getVerificationStatus()));
                });
    }

    private boolean conditionApplies(
            ServiceCustomField field, Map<String, ServiceCustomField> byCode,
            Map<Long, ProviderCustomFieldSubmission> submissions) {
        if (field.getConditionJson() == null || field.getConditionJson().isBlank()) return true;
        try {
            JsonNode condition = objectMapper.readTree(field.getConditionJson());
            String code = condition.path("fieldCode").asText();
            if (code.isBlank()) return true;
            ServiceCustomField parent = byCode.get(code);
            ProviderCustomFieldSubmission submission = parent == null
                    ? null : submissions.get(parent.getFieldId());
            if (submission == null) return false;
            JsonNode actual = objectMapper.readTree(submission.getValueJson());
            if (actual.has("value")) actual = actual.get("value");
            JsonNode expected = condition.get("value");
            String operator = condition.path("operator").asText("EQUALS");
            return "NOT_EQUALS".equals(operator) ? !actual.equals(expected) : actual.equals(expected);
        } catch (Exception ignored) {
            return false;
        }
    }
}
