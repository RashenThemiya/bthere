package com.jobhub.dto.provider;

/** Source assignment identifies the existing answer, schedule and service-area update APIs. */
public record SharedProviderOptionRegistrationResponse(Long optionId, Long providerOptionId,
        Long assignmentId, Long registrationServiceTypeId, String providerStatus,
        String adminStatus, String verificationStatus) {}
