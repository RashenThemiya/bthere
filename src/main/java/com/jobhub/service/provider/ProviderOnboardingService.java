package com.jobhub.service.provider;

import com.jobhub.dto.provider.ProviderOnboardingResponse;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import com.jobhub.repository.provider.ServiceProviderMarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProviderOnboardingService {

    private final ServiceProviderRepository providerRepository;
    private final ServiceProviderMarketRepository marketRepository;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ServiceProviderTypeRepository typeRepository;
    private final ProviderServiceApprovalService approvalService;

    @Transactional(readOnly = true)
    public ProviderOnboardingResponse get(Long userId) {
        ServiceProvider provider = providerRepository.findByUserId(userId).orElse(null);
        if (provider == null) {
            return new ProviderOnboardingResponse(false, false, false, false,
                    0, "COMPLETE_PROFILE", List.of());
        }
        boolean marketSelected = marketRepository.findByServiceProviderIdAndStatus(
                provider.getServiceProviderId(), "ACTIVE").isPresent();
        List<ServiceProviderTypeAssignment> assignments = assignmentRepository
                .findAllByServiceProviderId(provider.getServiceProviderId());
        Map<Long, ServiceProviderType> types = new HashMap<>();
        typeRepository.findAllById(assignments.stream()
                        .map(ServiceProviderTypeAssignment::getServiceProviderTypeId).toList())
                .forEach(type -> types.put(type.getServiceProviderTypeId(), type));

        List<ProviderOnboardingResponse.ServiceProgress> progress = assignments.stream()
                .map(item -> new ProviderOnboardingResponse.ServiceProgress(
                        item.getAssignmentId(), item.getServiceProviderTypeId(),
                        types.containsKey(item.getServiceProviderTypeId())
                                ? types.get(item.getServiceProviderTypeId()).getName() : null,
                        approvalService.documentsComplete(item),
                        approvalService.skillsComplete(item),
                        approvalService.certificatesComplete(item),
                        approvalService.educationComplete(item),
                        approvalService.availabilityComplete(item),
                        approvalService.serviceAreasComplete(item),
                        approvalService.optionsComplete(item),
                        approvalService.customRequirementsComplete(item),
                        item.getVerificationStatus(), item.getStatus()))
                .toList();
        boolean serviceSelected = !assignments.isEmpty();
        boolean requirementsEntered = !progress.isEmpty() && progress.stream().allMatch(item ->
                item.documentsCompleted() && item.skillsCompleted()
                        && item.certificatesCompleted() && item.educationCompleted()
                        && item.availabilityCompleted() && item.serviceAreasCompleted()
                        && item.optionsCompleted() && item.customRequirementsCompleted());
        boolean approved = !progress.isEmpty() && progress.stream()
                .allMatch(item -> "APPROVED".equals(item.verificationStatus()));

        String nextStep = !marketSelected ? "SELECT_MARKET"
                : !serviceSelected ? "SELECT_SERVICE"
                : !requirementsEntered ? "COMPLETE_SERVICE_REQUIREMENTS"
                : !approved ? "AWAIT_ADMIN_APPROVAL" : "COMPLETED";
        int percentage = !marketSelected ? 25 : !serviceSelected ? 50
                : !requirementsEntered ? 75 : approved ? 100 : 90;
        return new ProviderOnboardingResponse(true, marketSelected, serviceSelected,
                approved, percentage, nextStep, progress);
    }
}
