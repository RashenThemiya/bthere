package com.jobhub.service.customer;

import com.jobhub.dto.job.RatingApi.*;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.Rating;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.job.RatingRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Customer ratings of providers. One rating per provider per completed booking; each rating keeps the
 * booking's service option so summaries can be shown per option as well as overall. The provider's
 * overall averageRating/totalRatings fields are recalculated whenever a rating is added or moderated.
 */
@Service @RequiredArgsConstructor
public class RatingService {
    static final Set<String> INACTIVE_ASSIGNMENTS = Set.of("CANCELLED", "REMOVED", "DECLINED");
    static final int MAX_PAGE_SIZE = 50;

    private final RatingRepository ratings;
    private final JobRepository jobs;
    private final JobProviderAssignmentRepository jobAssignments;
    private final CustomerRepository customers;
    private final ServiceProviderRepository providers;
    private final ServiceOptionRepository options;
    private final AuditService audit;

    // ---------- customer ----------

    @Transactional
    public RatingView rate(Long userId, Long jobId, Rate input) {
        var customer = customer(userId);
        var job = ownedJob(customer, jobId);
        if (!"COMPLETED".equals(job.getJobStatus()))
            throw new ConflictException("Only a completed booking can be rated");
        List<Long> onJob = providerIds(job);
        Long providerId = input.providerId();
        if (providerId == null) {
            if (onJob.size() != 1) throw new IllegalArgumentException("providerId is required when a booking has more than one provider");
            providerId = onJob.get(0);
        } else if (!onJob.contains(providerId)) {
            throw new IllegalArgumentException("This provider did not work on this booking");
        }
        if (ratings.findByJobIdAndServiceProviderId(jobId, providerId).isPresent())
            throw new ConflictException("You have already rated this provider for this booking");
        var provider = providers.lockById(providerId).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        Rating rating = new Rating();
        rating.setJobId(jobId);
        rating.setServiceProviderId(providerId);
        rating.setCustomerId(customer.getCustomerId());
        rating.setServiceTypeId(job.getServiceProviderTypeId());
        rating.setServiceOptionId(job.getServiceOptionId());
        rating.setRating(input.rating());
        rating.setComment(trim(input.comment()));
        rating.setStatus(Rating.VISIBLE);
        rating = ratings.saveAndFlush(rating);
        refreshProvider(provider);
        return view(rating);
    }

    @Transactional(readOnly = true)
    public JobRatings jobRatings(Long userId, Long jobId) {
        var job = ownedJob(customer(userId), jobId);
        Map<Long, Rating> byProvider = new HashMap<>();
        ratings.findAllByJobIdOrderByRatingIdAsc(jobId).forEach(r -> byProvider.put(r.getServiceProviderId(), r));
        List<JobProviderRating> lines = new ArrayList<>();
        for (Long providerId : providerIds(job)) {
            var provider = providers.findById(providerId).orElse(null);
            Rating r = byProvider.get(providerId);
            lines.add(new JobProviderRating(providerId, provider == null ? null : name(provider),
                    provider == null ? null : provider.getProfilePhoto(), r != null, r == null ? null : view(r)));
        }
        boolean completed = "COMPLETED".equals(job.getJobStatus());
        return new JobRatings(jobId, job.getServiceOptionId(), job.getJobStatus(),
                completed && lines.stream().anyMatch(l -> !l.rated()), lines);
    }

    // ---------- summaries and reviews (customer and provider apps) ----------

    @Transactional(readOnly = true)
    public ProviderSummary summary(Long providerId, Long optionId) {
        var provider = providers.findById(providerId).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        Map<Long, int[]> byOption = new LinkedHashMap<>();
        int[] all = new int[6];
        for (Object[] row : ratings.countByOptionAndScore(providerId)) {
            Long option = (Long) row[0];
            int score = ((Number) row[1]).intValue();
            int count = ((Number) row[2]).intValue();
            if (score < 1 || score > 5) continue;
            all[score] += count;
            if (option != null) byOption.computeIfAbsent(option, k -> new int[6])[score] += count;
        }
        if (optionId != null) byOption.keySet().retainAll(Set.of(optionId));
        Map<Long, String> names = optionNames(byOption.keySet());
        List<OptionStats> optionStats = byOption.entrySet().stream()
                .map(e -> { Stats s = stats(e.getValue());
                    return new OptionStats(e.getKey(), names.get(e.getKey()), s.averageRating(), s.totalRatings(), s.distribution()); })
                .sorted(Comparator.comparingInt(OptionStats::totalRatings).reversed().thenComparing(OptionStats::optionId))
                .toList();
        return new ProviderSummary(providerId, name(provider), stats(all), optionStats);
    }

    @Transactional(readOnly = true)
    public Page<Review> reviews(Long providerId, Long optionId, int page, int size) {
        if (providers.findById(providerId).isEmpty()) throw new ResourceNotFoundException("Provider not found");
        var found = ratings.findVisible(providerId, optionId, pageable(page, size));
        Map<Long, String> names = optionNames(found.stream().map(Rating::getServiceOptionId).filter(Objects::nonNull).toList());
        Map<Long, Customer> people = customersById(found.stream().map(Rating::getCustomerId).toList());
        return found.map(r -> new Review(r.getRatingId(), r.getServiceOptionId(), names.get(r.getServiceOptionId()),
                r.getRating(), r.getComment(), shortName(people.get(r.getCustomerId())), r.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public ProviderSummary mySummary(Long userId, Long optionId) {
        return summary(providerIdOf(userId), optionId);
    }

    @Transactional(readOnly = true)
    public Page<Review> myReviews(Long userId, Long optionId, int page, int size) {
        return reviews(providerIdOf(userId), optionId, page, size);
    }

    /** Average and count per provider for one option; used to decorate search candidates. */
    @Transactional(readOnly = true)
    public Map<Long, Stats> optionSummaries(Long optionId, Collection<Long> providerIds) {
        if (optionId == null || providerIds == null || providerIds.isEmpty()) return Map.of();
        Map<Long, Stats> out = new HashMap<>();
        for (Object[] row : ratings.optionSummaries(optionId, providerIds)) {
            int count = ((Number) row[2]).intValue();
            out.put((Long) row[0], new Stats(average(((Number) row[1]).doubleValue()), count, Map.of()));
        }
        return out;
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public Page<AdminRating> adminSearch(Long providerId, Long optionId, String status, int page, int size) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !Set.of(Rating.VISIBLE, Rating.HIDDEN).contains(normalized))
            throw new IllegalArgumentException("Status must be VISIBLE or HIDDEN");
        var found = ratings.search(providerId, optionId, normalized, pageable(page, size));
        Map<Long, Customer> people = customersById(found.stream().map(Rating::getCustomerId).toList());
        Map<Long, String> providerNames = new HashMap<>();
        providers.findAllById(found.stream().map(Rating::getServiceProviderId).distinct().toList())
                .forEach(p -> providerNames.put(p.getServiceProviderId(), name(p)));
        return found.map(r -> adminView(r, providerNames.get(r.getServiceProviderId()), people.get(r.getCustomerId())));
    }

    @Transactional
    public AdminRating moderate(Long actorId, Long ratingId, Moderate input) {
        var rating = ratings.findById(ratingId).orElseThrow(() -> new ResourceNotFoundException("Rating not found"));
        var provider = providers.lockById(rating.getServiceProviderId()).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        String old = rating.getStatus();
        rating.setStatus(input.status());
        rating.setModerationReason(trim(input.reason()));
        rating.setModeratedBy(actorId);
        rating = ratings.saveAndFlush(rating);
        refreshProvider(provider);
        audit.record(actorId, "RATING_MODERATED", "RATING", ratingId, Map.of("status", String.valueOf(old)),
                Map.of("status", input.status(), "reason", String.valueOf(rating.getModerationReason())));
        Customer customer = rating.getCustomerId() == null ? null : customers.findById(rating.getCustomerId()).orElse(null);
        return adminView(rating, name(provider), customer);
    }

    // ---------- helpers ----------

    private void refreshProvider(ServiceProvider provider) {
        List<Object[]> rows = ratings.overall(provider.getServiceProviderId());
        Object[] row = rows.isEmpty() ? new Object[]{null, 0L} : rows.get(0);
        int count = row[1] == null ? 0 : ((Number) row[1]).intValue();
        provider.setTotalRatings(count);
        provider.setAverageRating(count == 0 || row[0] == null ? BigDecimal.ZERO.setScale(2) : average(((Number) row[0]).doubleValue()));
        providers.save(provider);
    }

    static Stats stats(int[] counts) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        int total = 0; long sum = 0;
        for (int score = 1; score <= 5; score++) {
            distribution.put(String.valueOf(score), counts[score]);
            total += counts[score]; sum += (long) score * counts[score];
        }
        BigDecimal avg = total == 0 ? null : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new Stats(avg, total, distribution);
    }

    private static BigDecimal average(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private Customer customer(Long userId) {
        return customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private Job ownedJob(Customer customer, Long jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException("Booking not found");
        return job;
    }

    /** The assigned provider plus any additional active providers on multi-provider jobs. */
    private List<Long> providerIds(Job job) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (job.getAssignedServiceProviderId() != null) ids.add(job.getAssignedServiceProviderId());
        jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(job.getJobId()).stream()
                .filter(a -> a.getStatus() == null || !INACTIVE_ASSIGNMENTS.contains(a.getStatus()))
                .forEach(a -> ids.add(a.getServiceProviderId()));
        return List.copyOf(ids);
    }

    private Long providerIdOf(Long userId) {
        return providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Provider not found")).getServiceProviderId();
    }

    private Map<Long, String> optionNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) return names;
        for (ServiceOption o : options.findAllById(new HashSet<>(ids))) names.put(o.getOptionId(), o.getName());
        return names;
    }

    private Map<Long, Customer> customersById(Collection<Long> ids) {
        Map<Long, Customer> out = new HashMap<>();
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!wanted.isEmpty()) customers.findAllById(wanted).forEach(c -> out.put(c.getCustomerId(), c));
        return out;
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be 0 or more");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "ratingId")));
    }

    static String name(ServiceProvider p) {
        String full = (Objects.toString(p.getFirstName(), "") + " " + Objects.toString(p.getLastName(), "")).trim();
        return full.isEmpty() ? null : full;
    }

    static String shortName(Customer c) {
        if (c == null || c.getFirstName() == null || c.getFirstName().isBlank()) return "Customer";
        String last = c.getLastName() == null || c.getLastName().isBlank() ? "" : " " + c.getLastName().trim().charAt(0) + ".";
        return c.getFirstName().trim() + last;
    }

    private static String fullName(Customer c) {
        if (c == null) return null;
        String full = (Objects.toString(c.getFirstName(), "") + " " + Objects.toString(c.getLastName(), "")).trim();
        return full.isEmpty() ? null : full;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static RatingView view(Rating r) {
        return new RatingView(r.getRatingId(), r.getJobId(), r.getServiceProviderId(), r.getServiceTypeId(),
                r.getServiceOptionId(), r.getRating(), r.getComment(), r.getStatus(), r.getCreatedAt());
    }

    private static AdminRating adminView(Rating r, String providerName, Customer customer) {
        return new AdminRating(r.getRatingId(), r.getJobId(), r.getServiceProviderId(), providerName, r.getCustomerId(),
                fullName(customer), r.getServiceTypeId(), r.getServiceOptionId(), r.getRating(), r.getComment(),
                r.getStatus(), r.getModerationReason(), r.getModeratedBy(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
