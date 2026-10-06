package com.jobhub.dto.provider;

import java.util.List;

public record ProviderOnboardingResponse(
        boolean profileCompleted,
        boolean marketSelected,
        boolean serviceSelected,
        boolean allActiveServicesApproved,
        int completionPercentage,
        String nextStep,
        List<ServiceProgress> services
) {
    public record ServiceProgress(
            Long assignmentId,
            Long serviceTypeId,
            String serviceName,
            boolean documentsCompleted,
            boolean skillsCompleted,
            boolean certificatesCompleted,
            boolean educationCompleted,
            boolean availabilityCompleted,
            boolean serviceAreasCompleted,
            boolean optionsCompleted,
            boolean customRequirementsCompleted,
            String verificationStatus,
            String effectiveStatus
    ) {}
}
