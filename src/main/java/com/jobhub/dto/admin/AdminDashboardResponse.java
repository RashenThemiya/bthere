package com.jobhub.dto.admin;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long totalUsers, long activeUsers, long inactiveUsers,
        long totalProviders, long verifiedProviders,
        long pendingProviderServices, long activeMarkets, long activeServiceTypes,
        long totalJobs, long pendingJobs, long completedJobs,
        long paidPayments, BigDecimal totalPaidAmount,
        ServiceConfigurationStatistics serviceConfiguration,
        ApprovalQueueStatistics approvalQueues,
        BookingStatistics bookingStatistics
) {
    public record ServiceConfigurationStatistics(
            long totalServiceTypes, long activeServiceTypes, long inactiveServiceTypes,
            long totalOptions, long activeOptions, long inactiveOptions,
            long timeBasedOptions, long routeBasedOptions,
            long oneAtATimeOptions, long manyAtATimeOptions,
            long oneToOneOptions, long manyCustomersOneProviderOptions,
            long oneCustomerManyProvidersOptions,
            long providerRequirementFields, long bookingFields,
            long totalMarketOfferings, long activeMarketOfferings,
            long totalRates, long activeRates
    ) {}

    public record ApprovalQueueStatistics(
            long providerServices, long serviceOptions, long documents,
            long skills, long certificates, long education,
            long customRequirements, long totalPending
    ) {}

    public record BookingStatistics(
            long timeBased, long routeBased,
            long oneToOne, long manyCustomersOneProvider,
            long oneCustomerManyProviders
    ) {}
}
