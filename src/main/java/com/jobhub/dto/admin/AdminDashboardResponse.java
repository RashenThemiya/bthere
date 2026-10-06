package com.jobhub.dto.admin;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long totalUsers, long activeUsers, long inactiveUsers,
        long totalProviders, long verifiedProviders,
        long pendingProviderServices, long activeMarkets, long activeServiceTypes,
        long totalJobs, long pendingJobs, long completedJobs,
        long paidPayments, BigDecimal totalPaidAmount
) {}
