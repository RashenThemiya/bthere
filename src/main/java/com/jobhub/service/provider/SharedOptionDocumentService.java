package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SharedOptionDocumentService {
    private final ServiceOptionRepository options;
    private final ServiceTypeDocumentRequirementRepository requirements;
    private final DocumentTypeRepository documentTypes;
    private final ProviderServiceOptionRepository registrations;
    private final ServiceProviderTypeAssignmentRepository assignments;
    private final ProviderServiceApprovalService approvals;

    @Transactional(readOnly = true)
    public List<ServiceDocumentRequirementResponse> get(Long optionId) {
        var option = option(optionId, false);
        return requirements.findAllByServiceProviderTypeId(option.getServiceProviderTypeId()).stream()
                .filter(r -> optionId.equals(r.getOptionId())).map(r -> {
                    var type = documentTypes.findById(r.getDocumentTypeId()).orElseThrow(() ->
                            new ResourceNotFoundException("Document type not found"));
                    return new ServiceDocumentRequirementResponse(type.getDocumentTypeId(), type.getName(),
                            type.getDescription(), type.isHasExpiry(), optionId, r.isMandatory(), r.isRequiresApproval());
                }).toList();
    }

    @Transactional
    public List<ServiceDocumentRequirementResponse> replace(Long optionId, OptionDocumentRequirementsRequest request) {
        var option = option(optionId, true);
        Set<Long> seen = new HashSet<>();
        for (var item : request.documents()) {
            if (!seen.add(item.documentTypeId())) throw new IllegalArgumentException("Duplicate document type");
            documentTypes.findById(item.documentTypeId()).filter(t -> "ACTIVE".equals(t.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Document type must exist and be active"));
        }
        var old = requirements.findAllByServiceProviderTypeId(option.getServiceProviderTypeId()).stream()
                .filter(r -> optionId.equals(r.getOptionId())).toList();
        requirements.deleteAll(old);
        requirements.flush();
        for (var item : request.documents()) {
            var entity = new ServiceTypeDocumentRequirement();
            entity.setServiceProviderTypeId(option.getServiceProviderTypeId());
            entity.setOptionId(optionId); entity.setDocumentTypeId(item.documentTypeId());
            entity.setMandatory(item.required()); entity.setRequiresApproval(item.requiresApproval());
            requirements.save(entity);
        }
        registrations.findAllByOptionIdIn(List.of(optionId)).stream()
                .map(r -> assignments.findById(r.getAssignmentId())).flatMap(Optional::stream)
                .map(ServiceProviderTypeAssignment::getServiceProviderId).distinct()
                .forEach(approvals::refreshProviderApprovals);
        return get(optionId);
    }

    private ServiceOption option(Long id, boolean lock) {
        return (lock ? options.findLockedById(id) : options.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
    }
}
