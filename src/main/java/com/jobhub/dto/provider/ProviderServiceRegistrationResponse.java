package com.jobhub.dto.provider;

public record ProviderServiceRegistrationResponse(
        ProviderTypeAssignmentResponse assignment,
        ProviderOnboardingResponse completion
) {}
