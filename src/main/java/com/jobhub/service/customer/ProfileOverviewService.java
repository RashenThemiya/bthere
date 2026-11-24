package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerRequirementsApi.FormItem;
import com.jobhub.dto.customer.ProfileOverviewApi.*;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCancellation;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only summary for the Customer Profile page. It reuses existing data and rules only: requirement
 * effective statuses come from CustomerRequirementsService, money from the booking/payment/cancellation records.
 * Nothing is inferred: unknown values are null, and payments whose outcome is not final are left out of totals.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileOverviewService {
    public static final int DEFAULT_RECENT_LIMIT = 5;
    public static final int MAX_RECENT_LIMIT = 20;

    static final Set<String> HISTORY_STATUSES = Set.of("COMPLETED", "CANCELLED");
    /** Payment captured; the booking's amount was taken (some may since be refunded). */
    static final Set<String> CAPTURED = Set.of("PAID", "SETTLED", "REFUNDED", "PARTIALLY_REFUNDED", "CANCELLATION_SETTLED");
    /** Payment captured but the final outcome is still being resolved by operations. */
    static final Set<String> UNDER_REVIEW = Set.of("PAID_REVIEW", "REFUND_REVIEW", "CHARGEDBACK");
    static final Set<String> LIVE_FUNDING = Set.of("PAYHERE");
    static final Set<String> TEST_FUNDING = Set.of("MOCK", "PAYHERE_SANDBOX");

    private final UserRepository users;

    private final com.jobhub.service.upload.ProfilePhotoUrls photoUrls;
    private final CustomerRepository customers;
    private final JobRepository jobs;
    private final BookingRequestRepository requests;
    private final JobCancellationRepository cancellations;
    private final ServiceProviderTypeRepository serviceTypes;
    private final ServiceOptionRepository options;
    private final CustomerRequirementsService requirements;
    private final ObjectMapper mapper;
    private final MarketRepository markets;
    /** Largest file the server accepts in one upload (multipart limit); null in unit tests means 10 MB. */
    @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:10MB}")
    private org.springframework.util.unit.DataSize uploadLimit;
    static final Map<String, String> CONTENT_TYPES = Map.of("PDF", "application/pdf", "JPG", "image/jpeg",
            "PNG", "image/png", "WEBP", "image/webp");
    static final String UPLOAD_ENDPOINT = "POST /api/v1/uploads/documents";

    public Overview overview(Long userId, int recentLimit) {
        if (recentLimit < 1 || recentLimit > MAX_RECENT_LIMIT)
            throw new IllegalArgumentException("recentLimit must be between 1 and " + MAX_RECENT_LIMIT);
        var user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var contacts = new Contacts(user.getEmail(), user.isEmailVerified(), user.getPhoneNumber(), user.isPhoneVerified());
        Customer customer = customers.findByUserId(userId).orElse(null);
        if (customer == null) {
            // No profile yet: nothing else can belong to this customer. Show contacts and empty sections.
            return new Overview(null, contacts, new Statistics(0, 0, 0, 0, 0, List.of(), List.of(), 0), List.of(),
                    new RecentList<>(List.of(), recentLimit, false, 0), new RecentList<>(List.of(), recentLimit, false, 0));
        }
        var names = new Names();
        List<Job> allJobs = jobs.findAllByCustomerIdOrderByCreatedAtDesc(customer.getCustomerId());
        Map<Long, BookingRequest> requestByJob = allJobs.isEmpty() ? Map.of() : requests
                .findAllByJobIdIn(allJobs.stream().map(Job::getJobId).toList()).stream()
                .collect(Collectors.toMap(BookingRequest::getJobId, Function.identity(), (a, b) -> a));
        List<BookingRequest> matching = requests.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, "MATCHING")
                .stream().filter(r -> r.getJobId() == null).toList();

        List<Job> history = allJobs.stream().filter(j -> in(HISTORY_STATUSES, j.getJobStatus())).toList();
        List<Job> active = allJobs.stream().filter(j -> !in(HISTORY_STATUSES, j.getJobStatus())).toList();

        List<PendingService> pending = new ArrayList<>();
        matching.forEach(r -> pending.add(pendingRequest(r, names)));
        active.forEach(j -> pending.add(pendingJob(j, requestByJob.get(j.getJobId()), names)));
        pending.sort(Comparator.comparing(PendingService::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));

        var stats = statistics(allJobs, requestByJob, matching.size(), active.size());
        var historyItems = history.stream().limit(recentLimit)
                .map(j -> historyItem(j, requestByJob.get(j.getJobId()), names)).toList();

        return new Overview(profile(customer), contacts, stats, documents(userId, names),
                new RecentList<>(pending.stream().limit(recentLimit).toList(), recentLimit, pending.size() > recentLimit, pending.size()),
                new RecentList<>(historyItems, recentLimit, history.size() > recentLimit, history.size()));
    }

    /** Every status a booking request can have (BookingRequestService). Unknown filter values are rejected. */
    public static final Set<String> REQUEST_STATUSES = Set.of("MATCHING", "AWAITING_CUSTOMER_CONFIRMATION",
            "PAYMENT_PENDING", "PAYMENT_RECONCILIATION", "PAYMENT_REVIEW", "CONFIRMED", "NO_SHOW_REVIEW", "IN_PROGRESS",
            "AWAITING_COMPLETION_CONFIRMATION", "COMPLETED", "CANCELLED", "UNMATCHED", "EXPIRED", "PAYMENT_FAILED");
    public static final int MAX_PAGE_SIZE = 50;

    /** The signed-in customer's own booking requests, newest first, optionally filtered by status. */
    public PageOf<RequestItem> bookingRequests(Long userId, List<String> statuses, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be 0 or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        Set<String> filter = new LinkedHashSet<>();
        if (statuses != null) for (String s : statuses) {
            if (s == null || s.isBlank()) continue;
            String v = s.trim().toUpperCase(Locale.ROOT);
            if (!REQUEST_STATUSES.contains(v)) throw new IllegalArgumentException("Unknown status: " + s.trim());
            filter.add(v);
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "requestId"));
        var result = filter.isEmpty() ? requests.findAllByUserId(userId, pageable)
                : requests.findAllByUserIdAndStatusIn(userId, filter, pageable);
        var names = new Names();
        List<RequestItem> items = result.getContent().stream().map(r -> requestItem(r, names)).toList();
        return new PageOf<>(items, page, size, result.getTotalElements(), result.hasNext());
    }

    private RequestItem requestItem(BookingRequest r, Names names) {
        JsonNode booking = bookingPayload(r);
        Long serviceTypeId = names.serviceTypeOf(r.getOptionId());
        LocalDateTime start = dateTime(booking, "startDatetime");
        if (start == null) start = dateTime(booking, "requestedPickupTime");
        LocalDateTime end = dateTime(booking, "expectedEndDatetime");
        var time = names.time(r.getMarketId(), start, end);
        return new RequestItem(r.getRequestId(), r.getJobId(), r.getStatus(), r.getPaymentStatus(), r.getPaymentFundingMode(),
                serviceTypeId, names.service(serviceTypeId), r.getOptionId(), names.option(r.getOptionId()), start,
                end, r.getQuotedAmount(), r.getCurrencyCode(), r.getCreatedAt(),
                time.marketId(), time.marketTimezone(), time.startUtc(), time.endUtc());
    }

    /** Set.of(...).contains(null) throws; stored statuses/funding modes can be null. */
    private static boolean in(Set<String> set, String value) { return value != null && set.contains(value); }

    private Profile profile(Customer c) {
        String name = (Objects.toString(c.getFirstName(), "") + " " + Objects.toString(c.getLastName(), "")).trim();
        return new Profile(c.getCustomerId(), c.getFirstName(), c.getLastName(), name.isEmpty() ? null : name,
                photoUrls.publicUrl(c.getProfilePhoto()), c.getStatus(), true);
    }

    // ---------- statistics ----------

    private Statistics statistics(List<Job> allJobs, Map<Long, BookingRequest> requestByJob, int awaitingProvider, int active) {
        Map<String, Money> live = new TreeMap<>(), test = new TreeMap<>();
        int excluded = 0;
        for (Job job : allJobs) {
            BookingRequest r = requestByJob.get(job.getJobId());
            String payment = r != null && r.getPaymentStatus() != null ? r.getPaymentStatus() : job.getPaymentStatus();
            boolean captured = in(CAPTURED, payment), reserved = "RESERVED".equals(payment);
            if (in(UNDER_REVIEW, payment)) { excluded++; continue; }
            if (!captured && !reserved) continue; // nothing was taken (not started, failed, released, unpaid)
            String funding = r == null ? null : r.getPaymentFundingMode();
            Map<String, Money> bucket = in(LIVE_FUNDING, funding) ? live : in(TEST_FUNDING, funding) ? test : null;
            if (bucket == null || job.getExpectedAmount() == null || job.getCurrencyCode() == null) { excluded++; continue; }
            BigDecimal refund = BigDecimal.ZERO;
            if (captured && !"PAID".equals(payment) && !"SETTLED".equals(payment)) {
                // Cancelled after payment: the refund is whatever the cancellation record says, never a guess.
                BigDecimal recorded = cancellations.findByJobId(job.getJobId()).map(JobCancellation::getRefundAmount).orElse(null);
                if (recorded == null && !"CANCELLATION_SETTLED".equals(payment)) { excluded++; continue; }
                if (recorded != null) refund = recorded;
            }
            Money m = bucket.computeIfAbsent(job.getCurrencyCode(), k -> new Money());
            if (reserved) { m.reserved = m.reserved.add(job.getExpectedAmount()); continue; }
            m.paid = m.paid.add(job.getExpectedAmount()); m.refunded = m.refunded.add(refund); m.count++;
        }
        int completed = (int) allJobs.stream().filter(j -> "COMPLETED".equals(j.getJobStatus())).count();
        int cancelled = (int) allJobs.stream().filter(j -> "CANCELLED".equals(j.getJobStatus())).count();
        return new Statistics(awaitingProvider + active, awaitingProvider, active, completed, cancelled,
                spending(live), spending(test), excluded);
    }

    private static List<Spending> spending(Map<String, Money> byCurrency) {
        return byCurrency.entrySet().stream().map(e -> new Spending(e.getKey(), e.getValue().paid, e.getValue().refunded,
                e.getValue().paid.subtract(e.getValue().refunded), e.getValue().reserved, e.getValue().count)).toList();
    }

    private static final class Money {
        BigDecimal paid = BigDecimal.ZERO, refunded = BigDecimal.ZERO, reserved = BigDecimal.ZERO;
        int count;
    }

    // ---------- documents ----------

    private List<Document> documents(Long userId, Names names) {
        return requirements.profileDocuments(userId).stream().map(item -> document(item, names)).toList();
    }

    /** The Profile documents list on its own (same items as profile-overview documents[]). */
    public List<Document> documents(Long userId) {
        return documents(userId, new Names());
    }

    /** One requirement by id, with its upload rules and the current submission; no service needed. */
    public Document document(Long userId, Long requirementId) {
        return document(requirements.requirementItem(userId, requirementId), new Names());
    }

    private Document document(FormItem item, Names names) {
        var r = item.requirement();
        var d = r.definition();
        var s = item.submission();
        List<DocumentService> services = d.targets().stream().filter(requirements::activeTarget)
                .map(t -> new DocumentService(t.scope(), t.serviceTypeId(), names.service(t.serviceTypeId()),
                        t.optionId(), names.option(t.optionId()))).toList();
        return new Document(r.requirementId(), d.code(), d.label(), d.fieldType(), d.required(), d.blocksBooking(),
                services, item.status(), s == null ? null : s.submissionId(),
                s == null ? null : s.reviewedAt(), s == null ? null : s.expiresOn(), s == null ? null : s.rejectionReason(),
                d.description(), d.verificationMode(), item.statusReason(), item.actionRequired(),
                uploadRules(d), fileKeys(s), r.version(), s == null ? null : s.definitionVersion(),
                s == null ? null : s.answerVersion());
    }

    private UploadRules uploadRules(com.jobhub.dto.customer.CustomerRequirementsApi.Definition d) {
        if (!CustomerRequirementsService.DOCUMENT_FIELD_TYPES.contains(d.fieldType())) return null;
        List<String> types = d.allowedFileTypes() == null || d.allowedFileTypes().isEmpty()
                ? List.of("PDF", "JPG", "PNG", "WEBP") : d.allowedFileTypes();
        if ("IMAGE_UPLOAD".equals(d.fieldType())) types = types.stream().filter(t -> !"PDF".equals(t)).toList();
        int serverMb = uploadLimit == null ? 10 : (int) Math.max(1, uploadLimit.toMegabytes());
        int definitionMb = d.maximumFileSizeMb() == null ? 100 : d.maximumFileSizeMb();
        return new UploadRules(d.minimumFiles() == null ? 1 : d.minimumFiles(), d.maximumFiles() == null ? 1 : d.maximumFiles(),
                Math.min(serverMb, definitionMb), types, types.stream().map(CONTENT_TYPES::get).toList(), UPLOAD_ENDPOINT);
    }

    private static List<String> fileKeys(com.jobhub.dto.customer.CustomerRequirementsApi.Submission s) {
        if (s == null || s.value() == null || !s.value().path("files").isArray()) return List.of();
        List<String> keys = new ArrayList<>();
        s.value().get("files").forEach(f -> { if (f.path("key").isTextual()) keys.add(f.get("key").asText()); });
        return keys;
    }

    // ---------- lists ----------

    private PendingService pendingRequest(BookingRequest r, Names names) {
        JsonNode booking = bookingPayload(r);
        Long optionId = r.getOptionId();
        Long serviceTypeId = names.serviceTypeOf(optionId);
        LocalDateTime start = dateTime(booking, "startDatetime");
        if (start == null) start = dateTime(booking, "requestedPickupTime");
        LocalDateTime end = dateTime(booking, "expectedEndDatetime");
        var time = names.time(r.getMarketId(), start, end);
        return new PendingService("REQUEST", r.getRequestId(), null, serviceTypeId, names.service(serviceTypeId),
                optionId, names.option(optionId), start, end,
                r.getStatus(), r.getPaymentStatus(), r.getCreatedAt(),
                time.marketId(), time.marketTimezone(), time.startUtc(), time.endUtc());
    }

    private PendingService pendingJob(Job j, BookingRequest r, Names names) {
        var time = names.time(j.getMarketId(), j.getStartDatetime(), j.getExpectedEndDatetime());
        return new PendingService("BOOKING", r == null ? null : r.getRequestId(), j.getJobId(), j.getServiceProviderTypeId(),
                names.service(j.getServiceProviderTypeId()), j.getServiceOptionId(), names.option(j.getServiceOptionId()),
                j.getStartDatetime(), j.getExpectedEndDatetime(), j.getJobStatus(), j.getPaymentStatus(), j.getCreatedAt(),
                time.marketId(), time.marketTimezone(), time.startUtc(), time.endUtc());
    }

    private HistoryItem historyItem(Job j, BookingRequest r, Names names) {
        LocalDateTime end = j.getActualEndDatetime() != null ? j.getActualEndDatetime() : j.getExpectedEndDatetime();
        var time = names.time(j.getMarketId(), j.getStartDatetime(), end);
        return new HistoryItem(j.getJobId(), r == null ? null : r.getRequestId(), j.getServiceProviderTypeId(),
                names.service(j.getServiceProviderTypeId()), j.getServiceOptionId(), names.option(j.getServiceOptionId()),
                j.getStartDatetime(), end, j.getJobStatus(), j.getExpectedAmount(), j.getCurrencyCode(), j.getPaymentStatus(),
                time.marketId(), time.marketTimezone(), time.startUtc(), time.endUtc());
    }

    private JsonNode bookingPayload(BookingRequest r) {
        if (r.getPayloadJson() == null) return null;
        try { return mapper.readTree(r.getPayloadJson()).path("booking"); } catch (Exception e) { return null; }
    }

    /** Accepts ISO text or Jackson's [y,m,d,h,mi(,s)] array form; anything else is null. */
    static LocalDateTime dateTime(JsonNode booking, String field) {
        if (booking == null) return null;
        JsonNode v = booking.path(field);
        try {
            if (v.isTextual()) return LocalDateTime.parse(v.asText());
            if (v.isArray() && v.size() >= 5) return LocalDateTime.of(v.get(0).asInt(), v.get(1).asInt(), v.get(2).asInt(),
                    v.get(3).asInt(), v.get(4).asInt(), v.size() > 5 ? v.get(5).asInt() : 0);
        } catch (Exception ignored) { }
        return null;
    }

    /** Per-call cache of service/option names. */
    private final class Names {
        private final Map<Long, String> serviceNames = new HashMap<>();
        private final Map<Long, Optional<ServiceOption>> optionRows = new HashMap<>();

        String service(Long id) {
            if (id == null) return null;
            return serviceNames.computeIfAbsent(id, k -> serviceTypes.findById(k).map(t -> t.getName()).orElse(null));
        }
        private Optional<ServiceOption> optionRow(Long id) {
            return optionRows.computeIfAbsent(id, options::findById);
        }
        String option(Long id) { return id == null ? null : optionRow(id).map(ServiceOption::getName).orElse(null); }
        Long serviceTypeOf(Long optionId) {
            return optionId == null ? null : optionRow(optionId).map(ServiceOption::getServiceProviderTypeId).orElse(null);
        }
        private final java.util.function.Function<Long, Optional<com.jobhub.entity.market.Market>> marketRows =
                MarketTime.cached(id -> markets.findById(id));
        MarketTime time(Long marketId, LocalDateTime start, LocalDateTime end) {
            return MarketTime.of(marketId, start, end, marketRows);
        }
    }
}
