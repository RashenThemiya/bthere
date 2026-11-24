package com.jobhub.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * One per requirement, reused by every service/option in services. status is the effective status (APPROVED,
     * PENDING, PROVIDED, REJECTED, EXPIRED, or MISSING when never submitted or when the definition changed since).
     * statusReason: NOT_SUBMITTED, DEFINITION_CHANGED, IN_REVIEW, SUPPORTING_EVIDENCE_CHANGED, REJECTED, EXPIRED,
     * or null. actionRequired: the customer must upload (MISSING, REJECTED, EXPIRED). fileKeys: keys of the
     * current submission's files (open them with GET /uploads/view-url). definitionVersion and
     * submittedDefinitionVersion differ when the admin changed the requirement after this submission.
     */
    public record Document(Long requirementId, String code, String name, String fieldType, boolean required,
                           boolean blocksBooking, List<DocumentService> services, String status, Long submissionId,
                           LocalDateTime reviewedAt, LocalDate expiresOn, String rejectionReason,
                           String description, String verificationMode, String statusReason, boolean actionRequired,
                           UploadRules upload, List<String> fileKeys, int definitionVersion,
                           Integer submittedDefinitionVersion, Integer answerVersion) {}

    /**
     * What the upload form accepts. maximumFileSizeMb is the smaller of the requirement's limit and the server's
     * upload limit. Upload every file with uploadEndpoint (POST /api/v1/uploads/documents, also for IMAGE_UPLOAD).
     */
    public record UploadRules(int minimumFiles, int maximumFiles, int maximumFileSizeMb, List<String> allowedFileTypes,
                              List<String> allowedContentTypes, String uploadEndpoint) {}

    /** scope GLOBAL means every service (ids/names null); SERVICE or OPTION name the service/option it applies to. */
    public record DocumentService(String scope, Long serviceTypeId, String serviceName, Long optionId, String optionName) {}

    /**
     * kind REQUEST: still looking for a provider (requestId set, jobId null).
     * kind BOOKING: a booking with a job (jobId set; requestId set when it came from a booking request).
     * startAt/endAt are market-local date-times, like other booking fields; startAtUtc/endAtUtc are the same as UTC
     * instants and marketTimezone is the IANA zone (same on RequestItem and HistoryItem).
     */
    public record PendingService(String kind, Long requestId, Long jobId, Long serviceTypeId, String serviceName,
                                 Long optionId, String optionName, LocalDateTime startAt, LocalDateTime endAt,
                                 String status, String paymentStatus, LocalDateTime createdAt,
                                 Long marketId, String marketTimezone, Instant startAtUtc, Instant endAtUtc) {}

    public record HistoryItem(Long jobId, Long requestId, Long serviceTypeId, String serviceName, Long optionId,
                              String optionName, LocalDateTime startAt, LocalDateTime endAt, String status,
                              BigDecimal amount, String currencyCode, String paymentStatus,
                              Long marketId, String marketTimezone, Instant startAtUtc, Instant endAtUtc) {}

    public record RecentList<T>(List<T> items, int limit, boolean hasMore, int totalCount) {}

    /** One booking request for the "View all" list. jobId is set once a provider accepted and a booking exists. */
    public record RequestItem(Long requestId, Long jobId, String status, String paymentStatus, String paymentFundingMode,
                              Long serviceTypeId, String serviceName, Long optionId, String optionName,
                              LocalDateTime startAt, LocalDateTime endAt, BigDecimal amount, String currencyCode,
                              LocalDateTime createdAt,
                              Long marketId, String marketTimezone, Instant startAtUtc, Instant endAtUtc) {}

    /** page is zero-based. */
    public record PageOf<T>(List<T> items, int page, int size, long totalCount, boolean hasMore) {}
}
