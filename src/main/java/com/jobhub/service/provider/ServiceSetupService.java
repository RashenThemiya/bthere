package com.jobhub.service.provider;

import com.jobhub.dto.provider.ServiceSetupRequest;
import com.jobhub.dto.provider.ServiceSetupResponse;
import com.jobhub.entity.provider.DocumentType;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.entity.provider.ServiceTypeDocumentRequirement;
import com.jobhub.entity.provider.ServiceTypeSkill;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.DocumentTypeRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.repository.provider.ServiceTypeDocumentRequirementRepository;
import com.jobhub.repository.provider.ServiceTypeSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServiceSetupService {

    private static final Set<String> LEVELS = Set.of("DISABLED", "OPTIONAL", "REQUIRED");

    private final ServiceProviderTypeRepository providerTypeRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ServiceTypeDocumentRequirementRepository requirementRepository;
    private final ServiceTypeSkillRepository skillRepository;

    @Transactional
    public ServiceSetupResponse create(ServiceSetupRequest request) {
        String name = request.name().trim();
        if (providerTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Provider type already exists");
        }

        ServiceProviderType type = new ServiceProviderType();
        type.setName(name);
        type.setDescription(optional(request.description()));
        type.setIconUrl(optional(request.iconUrl()));
        type.setStatus("ACTIVE");
        applySectionConfiguration(type, request);
        type = providerTypeRepository.save(type);
        replaceRequirements(type.getServiceProviderTypeId(), request);
        return get(type.getServiceProviderTypeId());
    }

    @Transactional
    public ServiceSetupResponse update(Long serviceTypeId, ServiceSetupRequest request) {
        ServiceProviderType type = requireType(serviceTypeId);
        String name = request.name().trim();
        if (!type.getName().equalsIgnoreCase(name)
                && providerTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Provider type already exists");
        }

        type.setName(name);
        type.setDescription(optional(request.description()));
        type.setIconUrl(optional(request.iconUrl()));
        applySectionConfiguration(type, request);
        providerTypeRepository.save(type);
        replaceRequirements(serviceTypeId, request);
        return get(serviceTypeId);
    }

    @Transactional
    public ServiceSetupResponse updateStatus(Long serviceTypeId, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }

        ServiceProviderType type = requireType(serviceTypeId);
        type.setStatus(status);
        providerTypeRepository.save(type);
        return get(serviceTypeId);
    }

    @Transactional(readOnly = true)
    public ServiceSetupResponse get(Long serviceTypeId) {
        ServiceProviderType type = requireType(serviceTypeId);
        List<ServiceTypeDocumentRequirement> documents =
                requirementRepository.findAllByServiceProviderTypeId(serviceTypeId);
        Map<Long, DocumentType> documentTypes = new HashMap<>();
        documentTypeRepository.findAllById(documents.stream()
                        .map(ServiceTypeDocumentRequirement::getDocumentTypeId)
                        .toList())
                .forEach(item -> documentTypes.put(item.getDocumentTypeId(), item));

        return new ServiceSetupResponse(
                type.getServiceProviderTypeId(),
                type.getName(),
                type.getDescription(),
                type.getIconUrl(),
                type.getStatus(),
                type.isAvailabilityEnabled(),
                type.isServiceAreasEnabled(),
                type.isOptionsRequired(),
                type.getMinimumOptionSelections(),
                type.getMaximumOptionSelections(),
                documents.stream().map(item -> {
                    DocumentType documentType = documentTypes.get(item.getDocumentTypeId());
                    return new ServiceSetupResponse.DocumentRequirement(
                            item.getDocumentTypeId(),
                            documentType == null ? null : documentType.getName(),
                            item.isMandatory(),
                            item.isRequiresApproval()
                    );
                }).toList(),
                skillRepository.findAllByServiceProviderTypeIdOrderByNameAsc(serviceTypeId)
                        .stream()
                        .map(item -> new ServiceSetupResponse.SkillRequirement(
                                item.getSkillId(),
                                item.getName(),
                                item.isMandatory(),
                                item.isRequiresApproval()
                        ))
                        .toList(),
                new ServiceSetupResponse.SectionRequirement(
                        normalizedLevel(type.getCertificateRequirement()),
                        type.isCertificateRequiresApproval()
                ),
                new ServiceSetupResponse.SectionRequirement(
                        normalizedLevel(type.getEducationRequirement()),
                        type.isEducationRequiresApproval()
                )
        );
    }

    private void replaceRequirements(Long serviceTypeId, ServiceSetupRequest request) {
        Set<Long> documentIds = new HashSet<>();
        for (ServiceSetupRequest.DocumentRequirement item : request.documents()) {
            if (!documentIds.add(item.documentTypeId())) {
                throw new IllegalArgumentException("Document requirements cannot contain duplicates");
            }
        }
        List<DocumentType> documentTypes = documentTypeRepository.findAllById(documentIds);
        if (documentTypes.size() != documentIds.size()
                || documentTypes.stream().anyMatch(item -> !"ACTIVE".equals(item.getStatus()))) {
            throw new IllegalArgumentException("One or more document types are invalid or inactive");
        }

        Set<String> skillNames = new HashSet<>();
        for (ServiceSetupRequest.SkillRequirement item : request.skills()) {
            if (!skillNames.add(item.name().trim().toLowerCase())) {
                throw new IllegalArgumentException("Skills cannot contain duplicates");
            }
        }

        requirementRepository.deleteAllByServiceProviderTypeId(serviceTypeId);
        requirementRepository.flush();
        for (ServiceSetupRequest.DocumentRequirement item : request.documents()) {
            ServiceTypeDocumentRequirement requirement = new ServiceTypeDocumentRequirement();
            requirement.setServiceProviderTypeId(serviceTypeId);
            requirement.setDocumentTypeId(item.documentTypeId());
            requirement.setMandatory(item.required());
            requirement.setRequiresApproval(item.requiresApproval());
            requirementRepository.save(requirement);
        }

        skillRepository.deleteAllByServiceProviderTypeId(serviceTypeId);
        skillRepository.flush();
        for (ServiceSetupRequest.SkillRequirement item : request.skills()) {
            ServiceTypeSkill skill = new ServiceTypeSkill();
            skill.setServiceProviderTypeId(serviceTypeId);
            skill.setName(item.name().trim());
            skill.setMandatory(item.required());
            skill.setRequiresApproval(item.requiresApproval());
            skill.setStatus("ACTIVE");
            skillRepository.save(skill);
        }
    }

    private void applySectionConfiguration(
            ServiceProviderType type,
            ServiceSetupRequest request
    ) {
        String certificateLevel = validateLevel(request.certificates().level());
        String educationLevel = validateLevel(request.education().level());
        type.setCertificateRequirement(certificateLevel);
        type.setCertificateRequiresApproval(
                !"DISABLED".equals(certificateLevel)
                        && request.certificates().requiresApproval()
        );
        type.setEducationRequirement(educationLevel);
        type.setEducationRequiresApproval(
                !"DISABLED".equals(educationLevel)
                        && request.education().requiresApproval()
        );
        type.setAvailabilityEnabled(request.availabilityEnabled());
        if (request.serviceAreasEnabled() != null) {
            type.setServiceAreasEnabled(request.serviceAreasEnabled());
        }
        int minimum = request.minimumOptionSelections() == null
                ? (request.optionsRequired() ? 1 : 0)
                : request.minimumOptionSelections();
        Integer maximum = request.maximumOptionSelections();
        if (minimum < 0 || maximum != null && maximum < minimum) {
            throw new IllegalArgumentException(
                    "Option selection limits are invalid");
        }
        type.setOptionsRequired(request.optionsRequired());
        type.setMinimumOptionSelections(minimum);
        type.setMaximumOptionSelections(maximum);
    }

    private String validateLevel(String level) {
        String normalized = level.trim().toUpperCase();
        if (!LEVELS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Requirement level must be DISABLED, OPTIONAL or REQUIRED"
            );
        }
        return normalized;
    }

    private String normalizedLevel(String level) {
        return level == null ? "OPTIONAL" : level;
    }

    private ServiceProviderType requireType(Long serviceTypeId) {
        return providerTypeRepository.findById(serviceTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider type was not found"
                ));
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
