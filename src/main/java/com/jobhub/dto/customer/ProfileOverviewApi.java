package com.jobhub.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** GET /api/v1/customers/me/profile-overview: everything the Customer Profile page displays, in one call. */
public final class ProfileOverviewApi {
    private ProfileOverviewApi() {}

    public record Overview(Profile profile, Contacts contacts, Statistics statistics, List<Document> documents,
                           RecentList<PendingService> pendingServices, RecentList<HistoryItem> serviceHistory) {}

    /** null when the customer has not created a profile yet (profileCompleted=false in that case). */
    public record Profile(Long customerId, String firstName, String lastName, String displayName,
                          String profilePhoto, String status, boolean profileCompleted) {}

    public record Contacts(String email, boolean emailVerified, String phoneNumber, boolean phoneVerified) {}

    /**
     * Counts are over all of the customer's bookings and open requests, not only the recent lists.
     * spending covers real (PAYHERE-funded) payments; testSpending covers MOCK and PAYHERE_SANDBOX payments.
     * excludedPaymentCount counts bookings whose money cannot be stated yet (under review, chargeback,
     * unknown funding source); they are in neither total.
     */
    public record Statistics(int pendingCount, int awaitingProviderCount, int activeCount, int completedCount,
                             int cancelledCount, List<Spending> spending, List<Spending> testSpending,
                             int excludedPaymentCount) {}

    /** totalSpent = paidAmount - refundedAmount. reservedAmount is wallet money held for unfinished bookings, not spent. */
    public record Spending(String currencyCode, BigDecimal paidAmount, BigDecimal refundedAmount,
                           BigDecimal totalSpent, BigDecimal reservedAmount, int paidBookingCount) {}

    /** status is the effective status (APPROVED, PENDING, REJECTED, EXPIRED, ...) or MISSING when never submitted. */
    public record Document(Long requirementId, String code, String name, String fieldType, boolean required,
                           boolean blocksBooking, List<DocumentService> services, String status, Long submissionId,
                           LocalDateTime reviewedAt, LocalDate expiresOn, String rejectionReason) {}

    /** scope GLOBAL means every service (ids/names null); SERVICE or OPTION name the service/option it applies to. */
    public record DocumentService(String scope, Long serviceTypeId, String serviceName, Long optionId, String optionName) {}

    /**
     * kind REQUEST: still looking for a provider (requestId set, jobId null).
     * kind BOOKING: a booking with a job (jobId set; requestId set when it came from a booking request).
     * startAt/endAt are market-local date-times, like other booking fields.
     */
    public record PendingService(String kind, Long requestId, Long jobId, Long serviceTypeId, String serviceName,
                                 Long optionId, String optionName, LocalDateTime startAt, LocalDateTime endAt,
                                 String status, String paymentStatus, LocalDateTime createdAt) {}

    public record HistoryItem(Long jobId, Long requestId, Long serviceTypeId, String serviceName, Long optionId,
                              String optionName, LocalDateTime startAt, LocalDateTime endAt, String status,
                              BigDecimal amount, String currencyCode, String paymentStatus) {}

    public record RecentList<T>(List<T> items, int limit, boolean hasMore, int totalCount) {}

    /** One booking request for the "View all" list. jobId is set once a provider accepted and a booking exists. */
    public record RequestItem(Long requestId, Long jobId, String status, String paymentStatus, String paymentFundingMode,
                              Long serviceTypeId, String serviceName, Long optionId, String optionName,
                              LocalDateTime startAt, LocalDateTime endAt, BigDecimal amount, String currencyCode,
                              LocalDateTime createdAt) {}

    /** page is zero-based. */
    public record PageOf<T>(List<T> items, int page, int size, long totalCount, boolean hasMore) {}
}
