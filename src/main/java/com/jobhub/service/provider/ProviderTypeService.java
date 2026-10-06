package com.jobhub.service.provider;

import com.jobhub.dto.provider.CreateProviderTypeRequest;
import com.jobhub.dto.provider.ProviderTypeAssignmentRequest;
import com.jobhub.dto.provider.ProviderTypeAssignmentResponse;
import com.jobhub.dto.provider.ProviderTypeResponse;
import com.jobhub.dto.provider.ServiceDocumentRequirementResponse;
import com.jobhub.dto.provider.UpdateProviderTypesRequest;
import com.jobhub.dto.provider.UpdateServiceDocumentRequirementsRequest;
import com.jobhub.dto.provider.EmergencyApprovalRequest;
import com.jobhub.entity.provider.DocumentType;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import com.jobhub.entity.provider.ServiceTypeDocumentRequirement;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.DocumentTypeRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.repository.provider.ServiceTypeDocumentRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import com.jobhub.service.audit.AuditService;

@Service
@RequiredArgsConstructor
public class ProviderTypeService {

    private final ProviderProfileService providerProfileService;
    private final ServiceProviderTypeRepository providerTypeRepository;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ServiceTypeDocumentRequirementRepository requirementRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ProviderServiceApprovalService approvalService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ProviderTypeResponse> listActiveTypes() {
        return providerTypeRepository.findAllByStatusOrderByNameAsc("ACTIVE")
                .stream()
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional
    public ProviderTypeResponse createType(CreateProviderTypeRequest request) {
        String name = request.name().trim();
        if (providerTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Provider type already exists");
        }

        ServiceProviderType type = new ServiceProviderType();
        type.setName(name);
        type.setDescription(optional(request.description()));
        type.setIconUrl(optional(request.iconUrl()));
        type.setStatus("ACTIVE");
        type.setCertificateRequirement("OPTIONAL");
        type.setEducationRequirement("OPTIONAL");
        return toTypeResponse(providerTypeRepository.save(type));
    }

    @Transactional(readOnly = true)
    public List<ProviderTypeAssignmentResponse> getAssignments(Long userId) {
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        List<ServiceProviderTypeAssignment> assignments = assignmentRepository
                .findAllByServiceProviderId(provider.getServiceProviderId());
        Map<Long, ServiceProviderType> types = typeMap(assignments.stream()
                .map(ServiceProviderTypeAssignment::getServiceProviderTypeId)
                .toList());
        return assignments.stream()
                .map(assignment -> toAssignmentResponse(
                        assignment,
                        types,
                        approvalService.satisfiedDocumentTypeIds(
                                provider.getServiceProviderId(),
                                assignment.getServiceProviderTypeId()
                        )
                ))
                .toList();
    }

    @Transactional
    public List<ProviderTypeAssignmentResponse> replaceAssignments(
            Long userId,
            UpdateProviderTypesRequest request
    ) {
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        Set<Long> requestedIds = new HashSet<>();
        for (ProviderTypeAssignmentRequest assignment : request.assignments()) {
            if (!requestedIds.add(assignment.providerTypeId())) {
                throw new IllegalArgumentException("Provider types cannot contain duplicates");
            }
        }

        List<ServiceProviderType> activeTypes = providerTypeRepository
                .findAllByServiceProviderTypeIdInAndStatus(requestedIds, "ACTIVE");
        if (activeTypes.size() != requestedIds.size()) {
            throw new IllegalArgumentException("One or more provider types are invalid or inactive");
        }

        Map<Long, ServiceProviderTypeAssignment> existing = new HashMap<>();
        assignmentRepository.findAllByServiceProviderId(provider.getServiceProviderId())
                .forEach(item -> existing.put(item.getServiceProviderTypeId(), item));

        for (ServiceProviderTypeAssignment assignment : existing.values()) {
            if (!requestedIds.contains(assignment.getServiceProviderTypeId())) {
                assignment.setProviderStatus("INACTIVE");
                normalizeAssignmentStatuses(assignment);
                assignmentRepository.save(assignment);
            }
        }
        for (ProviderTypeAssignmentRequest item : request.assignments()) {
            ServiceProviderTypeAssignment assignment = existing.get(
                    item.providerTypeId()
            );
            if (assignment == null) {
                assignment = newAssignment(provider.getServiceProviderId(), item);
            } else {
                assignment.setExperienceYears(item.experienceYears());
                assignment.setProviderStatus("ACTIVE");
                normalizeAssignmentStatuses(assignment);
            }
            assignmentRepository.save(assignment);
        }

        approvalService.refreshProviderApprovals(provider.getServiceProviderId());
        List<ServiceProviderTypeAssignment> saved = assignmentRepository.findAllByServiceProviderId(
                provider.getServiceProviderId()
        );

        Map<Long, ServiceProviderType> types = typeMap(saved.stream()
                .map(ServiceProviderTypeAssignment::getServiceProviderTypeId)
                .toList());
        return saved.stream()
                .map(assignment -> toAssignmentResponse(
                        assignment,
                        types,
                        approvalService.satisfiedDocumentTypeIds(
                                provider.getServiceProviderId(),
                                assignment.getServiceProviderTypeId()
                        )
                ))
                .toList();
    }

    @Transactional
    public ProviderTypeAssignmentResponse addAssignment(
            Long userId,
            ProviderTypeAssignmentRequest request
    ) {
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        ServiceProviderType type = providerTypeRepository
                .findById(request.providerTypeId())
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider type was not found or is inactive"
                ));

        ServiceProviderTypeAssignment assignment = assignmentRepository
                .findByServiceProviderIdAndServiceProviderTypeId(
                        provider.getServiceProviderId(),
                        request.providerTypeId()
                )
                .orElseGet(() -> newAssignment(
                        provider.getServiceProviderId(),
                        request
                ));
        assignment.setExperienceYears(request.experienceYears());
        assignment.setProviderStatus("ACTIVE");
        normalizeAssignmentStatuses(assignment);
        assignment = assignmentRepository.save(assignment);
        approvalService.refreshProviderApprovals(provider.getServiceProviderId());
        assignment = assignmentRepository.findById(assignment.getAssignmentId())
                .orElse(assignment);
        return toAssignmentResponse(
                assignment,
                Map.of(type.getServiceProviderTypeId(), type),
                approvalService.satisfiedDocumentTypeIds(
                        provider.getServiceProviderId(),
                        type.getServiceProviderTypeId()
                )
        );
    }

    @Transactional
    public ProviderTypeAssignmentResponse updateProviderStatus(
            Long userId,
            Long assignmentId,
            String requestedStatus
    ) {
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        ServiceProviderTypeAssignment assignment = assignmentRepository
                .findByAssignmentIdAndServiceProviderId(
                        assignmentId,
                        provider.getServiceProviderId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider service assignment was not found"
                ));
        assignment.setProviderStatus(validateActiveStatus(requestedStatus));
        normalizeAssignmentStatuses(assignment);
        assignmentRepository.save(assignment);
        return responseForAssignment(assignment);
    }

    @Transactional
    public ProviderTypeAssignmentResponse updateAdminStatus(
            Long assignmentId,
            String requestedStatus
    ) {
        ServiceProviderTypeAssignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider service assignment was not found"
                ));
        assignment.setAdminStatus(validateActiveStatus(requestedStatus));
        normalizeAssignmentStatuses(assignment);
        assignmentRepository.save(assignment);
        return responseForAssignment(assignment);
    }

    @Transactional
    public ProviderTypeAssignmentResponse applyEmergencyOverride(
            Long actorId, Long assignmentId, EmergencyApprovalRequest request) {
        ServiceProviderTypeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider service assignment was not found"));
        assignment.setEmergencyOverride(true);
        assignment.setEmergencyOverrideReason(request.reason().trim());
        assignment.setEmergencyOverrideBy(actorId);
        assignment.setEmergencyOverrideAt(LocalDateTime.now());
        assignment.setEmergencyOverrideExpiresAt(request.expiresAt());
        assignment.setVerificationStatus("APPROVED");
        assignmentRepository.save(assignment);
        auditService.record(actorId, "EMERGENCY_SERVICE_APPROVAL", "PROVIDER_SERVICE",
                assignmentId, null, java.util.Map.of("reason", request.reason()));
        return responseForAssignment(assignment);
    }

    @Transactional
    public ProviderTypeAssignmentResponse revokeEmergencyOverride(Long actorId, Long assignmentId) {
        ServiceProviderTypeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider service assignment was not found"));
        assignment.setEmergencyOverride(false);
        assignment.setEmergencyOverrideReason(null);
        assignment.setEmergencyOverrideBy(null);
        assignment.setEmergencyOverrideAt(null);
        assignment.setEmergencyOverrideExpiresAt(null);
        assignmentRepository.save(assignment);
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        assignment = assignmentRepository.findById(assignmentId).orElse(assignment);
        auditService.record(actorId, "EMERGENCY_SERVICE_APPROVAL_REVOKED",
                "PROVIDER_SERVICE", assignmentId, null, null);
        return responseForAssignment(assignment);
    }

    @Transactional(readOnly = true)
    public List<ServiceDocumentRequirementResponse> getDocumentRequirements(
            Long providerTypeId
    ) {
        requireProviderType(providerTypeId);
        List<ServiceTypeDocumentRequirement> requirements =
                requirementRepository.findAllByServiceProviderTypeId(providerTypeId);
        Map<Long, DocumentType> types = new HashMap<>();
        documentTypeRepository.findAllById(requirements.stream()
                        .map(ServiceTypeDocumentRequirement::getDocumentTypeId)
                        .toList())
                .forEach(type -> types.put(type.getDocumentTypeId(), type));
        return requirements.stream()
                .filter(requirement -> types.containsKey(requirement.getDocumentTypeId()))
                .map(requirement -> {
                    DocumentType type = types.get(requirement.getDocumentTypeId());
                    return new ServiceDocumentRequirementResponse(
                            type.getDocumentTypeId(),
                            type.getName(),
                            type.getDescription(),
                            type.isHasExpiry(),
                            requirement.isMandatory(),
                            requirement.isRequiresApproval()
                    );
                })
                .toList();
    }

    @Transactional
    public List<ServiceDocumentRequirementResponse> replaceDocumentRequirements(
            Long providerTypeId,
            UpdateServiceDocumentRequirementsRequest request
    ) {
        requireProviderType(providerTypeId);
        Set<Long> ids = new HashSet<>(request.documentTypeIds());
        if (ids.size() != request.documentTypeIds().size()) {
            throw new IllegalArgumentException("Document types cannot contain duplicates");
        }
        List<DocumentType> types = documentTypeRepository.findAllById(ids);
        if (types.size() != ids.size()
                || types.stream().anyMatch(type -> !"ACTIVE".equals(type.getStatus()))) {
            throw new IllegalArgumentException("One or more document types are invalid or inactive");
        }

        requirementRepository.deleteAllByServiceProviderTypeId(providerTypeId);
        for (Long documentTypeId : request.documentTypeIds()) {
            ServiceTypeDocumentRequirement requirement =
                    new ServiceTypeDocumentRequirement();
            requirement.setServiceProviderTypeId(providerTypeId);
            requirement.setDocumentTypeId(documentTypeId);
            requirement.setMandatory(true);
            requirement.setRequiresApproval(true);
            requirementRepository.save(requirement);
        }

        assignmentRepository.findAllByServiceProviderTypeId(providerTypeId).stream()
                .map(ServiceProviderTypeAssignment::getServiceProviderId)
                .distinct()
                .forEach(approvalService::refreshProviderApprovals);
        return getDocumentRequirements(providerTypeId);
    }

    private ServiceProviderTypeAssignment newAssignment(
            Long providerId,
            ProviderTypeAssignmentRequest request
    ) {
        ServiceProviderTypeAssignment assignment = new ServiceProviderTypeAssignment();
        assignment.setServiceProviderId(providerId);
        assignment.setServiceProviderTypeId(request.providerTypeId());
        assignment.setExperienceYears(request.experienceYears());
        assignment.setProviderStatus("ACTIVE");
        assignment.setAdminStatus("ACTIVE");
        assignment.setStatus("ACTIVE");
        assignment.setVerificationStatus("PENDING");
        return assignment;
    }

    private Map<Long, ServiceProviderType> typeMap(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ServiceProviderType> result = new HashMap<>();
        providerTypeRepository.findAllById(ids)
                .forEach(type -> result.put(type.getServiceProviderTypeId(), type));
        return result;
    }

    private ProviderTypeAssignmentResponse toAssignmentResponse(
            ServiceProviderTypeAssignment assignment,
            Map<Long, ServiceProviderType> types,
            Set<Long> approvedDocuments
    ) {
        ServiceProviderType type = types.get(assignment.getServiceProviderTypeId());
        Set<Long> required = approvalService.requiredDocumentTypeIds(
                assignment.getServiceProviderTypeId()
        );
        List<Long> missing = required.stream()
                .filter(id -> !approvedDocuments.contains(id))
                .sorted()
                .toList();
        return new ProviderTypeAssignmentResponse(
                assignment.getAssignmentId(),
                assignment.getServiceProviderTypeId(),
                type == null ? null : type.getName(),
                assignment.getExperienceYears(),
                effectiveStatus(assignment),
                normalizedStatus(assignment.getProviderStatus()),
                normalizedStatus(assignment.getAdminStatus()),
                assignment.getVerificationStatus(),
                assignment.isEmergencyOverride(),
                assignment.getEmergencyOverrideReason(),
                assignment.getEmergencyOverrideExpiresAt(),
                required.stream().sorted().toList(),
                missing
        );
    }

    private ProviderTypeAssignmentResponse responseForAssignment(
            ServiceProviderTypeAssignment assignment
    ) {
        ServiceProviderType type = requireProviderType(
                assignment.getServiceProviderTypeId()
        );
        return toAssignmentResponse(
                assignment,
                Map.of(type.getServiceProviderTypeId(), type),
                approvalService.satisfiedDocumentTypeIds(
                        assignment.getServiceProviderId(),
                        assignment.getServiceProviderTypeId()
                )
        );
    }

    private String validateActiveStatus(String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        return status;
    }

    private void normalizeAssignmentStatuses(ServiceProviderTypeAssignment assignment) {
        assignment.setProviderStatus(normalizedStatus(assignment.getProviderStatus()));
        assignment.setAdminStatus(normalizedStatus(assignment.getAdminStatus()));
        assignment.setStatus(effectiveStatus(assignment));
    }

    private String effectiveStatus(ServiceProviderTypeAssignment assignment) {
        return "ACTIVE".equals(normalizedStatus(assignment.getProviderStatus()))
                && "ACTIVE".equals(normalizedStatus(assignment.getAdminStatus()))
                ? "ACTIVE"
                : "INACTIVE";
    }

    private String normalizedStatus(String status) {
        return status == null ? "ACTIVE" : status;
    }

    private ServiceProviderType requireProviderType(Long providerTypeId) {
        return providerTypeRepository.findById(providerTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider type was not found"
                ));
    }

    private ProviderTypeResponse toTypeResponse(ServiceProviderType type) {
        return new ProviderTypeResponse(
                type.getServiceProviderTypeId(),
                type.getName(),
                type.getDescription(),
                type.getIconUrl(),
                type.getStatus()
        );
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
