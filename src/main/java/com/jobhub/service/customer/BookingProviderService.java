package com.jobhub.service.customer;

import com.jobhub.dto.job.BookingProviderApi.BookingProvider;
import com.jobhub.dto.job.BookingProviderApi.BookingProviders;
import com.jobhub.dto.job.BookingProviderApi.Contact;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.job.RatingRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Assigned provider details for the customer's own booking. Name, photo and rating are always shown
 * (including cancelled/completed history); the phone is released only while the booking is paid and active.
 */
@Service
@RequiredArgsConstructor
public class BookingProviderService {
    /** Paid, not yet finished: the only states in which the customer may call the provider. */
    static final List<String> CONTACT_ALLOWED_STATUSES = List.of("CONFIRMED", "IN_PROGRESS");
    /** Before payment/confirmation the phone stays hidden so bookings cannot be moved off-platform. */
    static final Set<String> NOT_CONFIRMED_STATUSES = Set.of("PENDING", "AWAITING_PAYMENT",
            "AWAITING_CUSTOMER_CONFIRMATION", "PAYMENT_PENDING", "PAYMENT_FAILED", "PAYMENT_RECONCILIATION");
    static final String BOOKING_NOT_CONFIRMED = "BOOKING_NOT_CONFIRMED";
    static final String BOOKING_ENDED = "BOOKING_ENDED";
    static final String PHONE_NOT_AVAILABLE = "PHONE_NOT_AVAILABLE";

    private final JobRepository jobs;

    private final com.jobhub.service.upload.ProfilePhotoUrls photoUrls;
    private final CustomerRepository customers;
    private final JobProviderAssignmentRepository jobAssignments;
    private final ServiceProviderRepository providers;
    private final UserRepository users;
    private final RatingRepository ratings;
    private final com.jobhub.repository.market.MarketRepository markets;

    @Transactional(readOnly = true)
    public BookingProviders providers(Long userId, Long jobId) {
        var customer = customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        Job job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        // Same 404 for "missing" and "someone else's" so booking IDs cannot be probed.
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException("Booking not found");

        List<BookingProvider> result = providerIds(job).stream()
                .map(id -> providers.findById(id).map(p -> view(job, p)).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        var times = ServiceTimes.of(job, id -> markets.findById(id));
        return new BookingProviders(job.getJobId(), job.getJobStatus(), job.getFulfillmentModel(),
                CONTACT_ALLOWED_STATUSES, result,
                times.actualStartedAt(), times.actualFinishedAt(), times.completionConfirmedAt());
    }

    private BookingProvider view(Job job, ServiceProvider p) {
        String role = p.getServiceProviderId().equals(job.getAssignedServiceProviderId()) ? "PRIMARY" : "TEAM_MEMBER";
        String name = (Objects.toString(p.getFirstName(), "") + " " + Objects.toString(p.getLastName(), "")).trim();
        boolean rated = ratings.findByJobIdAndServiceProviderId(job.getJobId(), p.getServiceProviderId()).isPresent();
        return new BookingProvider(p.getServiceProviderId(), role, name, photoUrls.publicUrl(p.getProfilePhoto()),
                p.getAverageRating(), p.getTotalRatings(), rated, contact(job, p));
    }

    private Contact contact(Job job, ServiceProvider p) {
        String status = job.getJobStatus();
        // List.of/Set.of contains(null) throws, so a missing status is treated as not confirmed.
        if (status == null) return new Contact(false, null, BOOKING_NOT_CONFIRMED);
        if (!CONTACT_ALLOWED_STATUSES.contains(status)) {
            return new Contact(false, null, NOT_CONFIRMED_STATUSES.contains(status) ? BOOKING_NOT_CONFIRMED : BOOKING_ENDED);
        }
        var user = p.getUserId() == null ? null : users.findById(p.getUserId()).orElse(null);
        String phone = user == null ? null : user.getPhoneNumber();
        if (phone == null || phone.isBlank() || !"ACTIVE".equals(user.getStatus()) || !"ACTIVE".equals(p.getStatus())) {
            return new Contact(false, null, PHONE_NOT_AVAILABLE);
        }
        return new Contact(true, phone, null);
    }

    /** The assigned provider plus active team providers; removed/declined/cancelled assignments are excluded. */
    private List<Long> providerIds(Job job) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (job.getAssignedServiceProviderId() != null) ids.add(job.getAssignedServiceProviderId());
        jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(job.getJobId()).stream()
                .filter(a -> a.getStatus() == null || !RatingService.INACTIVE_ASSIGNMENTS.contains(a.getStatus()))
                .forEach(a -> ids.add(a.getServiceProviderId()));
        return List.copyOf(ids);
    }
}
