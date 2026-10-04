package com.jobhub.service.provider;

import com.jobhub.entity.provider.ServiceProviderDocument;
import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import com.jobhub.entity.provider.ServiceTypeDocumentRequirement;
import com.jobhub.repository.provider.ServiceProviderDocumentRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.repository.provider.ServiceTypeDocumentRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProviderServiceApprovalService {

    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ServiceTypeDocumentRequirementRepository requirementRepository;
    private final ServiceProviderDocumentRepository documentRepository;

    public void refreshProviderApprovals(Long providerId) {
        List<ServiceProviderTypeAssignment> assignments =
                assignmentRepository.findAllByServiceProviderId(providerId);

        for (ServiceProviderTypeAssignment assignment : assignments) {
            Set<Long> required = requiredDocumentTypeIds(
                    assignment.getServiceProviderTypeId()
            );
            Set<Long> satisfied = satisfiedDocumentTypeIds(
                    providerId,
                    assignment.getServiceProviderTypeId()
            );
            assignment.setVerificationStatus(
                    satisfied.containsAll(required) ? "APPROVED" : "PENDING"
            );
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
}
