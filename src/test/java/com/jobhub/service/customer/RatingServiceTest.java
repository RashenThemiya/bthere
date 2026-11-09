package com.jobhub.service.customer;

import com.jobhub.dto.job.RatingApi.*;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobProviderAssignment;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingServiceTest {
    @Mock RatingRepository ratings;
    @Mock JobRepository jobs;
    @Mock JobProviderAssignmentRepository jobAssignments;
    @Mock CustomerRepository customers;
    @Mock ServiceProviderRepository providers;
    @Mock ServiceOptionRepository options;
    @Mock AuditService audit;
    @InjectMocks RatingService service;

    Customer customer;
    Job job;
    ServiceProvider provider;
    List<Rating> stored = new ArrayList<>();

    @BeforeEach void setup() {
        customer = new Customer(); customer.setCustomerId(4L); customer.setUserId(40L);
        customer.setFirstName("Nimali"); customer.setLastName("Silva");
        job = new Job(); job.setJobId(70L); job.setCustomerId(4L); job.setServiceProviderTypeId(21L);
        job.setServiceOptionId(12L); job.setAssignedServiceProviderId(18L); job.setJobStatus("COMPLETED");
        provider = new ServiceProvider(); provider.setServiceProviderId(18L); provider.setUserId(180L);
        provider.setFirstName("Kumari"); provider.setLastName("Perera");
        provider.setAverageRating(BigDecimal.ZERO); provider.setTotalRatings(0);
        when(customers.findByUserId(40L)).thenReturn(Optional.of(customer));
        when(jobs.findById(70L)).thenReturn(Optional.of(job));
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(70L)).thenReturn(List.of());
        when(providers.lockById(18L)).thenReturn(Optional.of(provider));
        when(providers.findById(18L)).thenReturn(Optional.of(provider));
        when(ratings.findByJobIdAndServiceProviderId(anyLong(), anyLong())).thenAnswer(inv -> stored.stream()
                .filter(r -> r.getJobId().equals(inv.getArgument(0)) && r.getServiceProviderId().equals(inv.getArgument(1))).findFirst());
        when(ratings.saveAndFlush(any(Rating.class))).thenAnswer(inv -> {
            Rating r = inv.getArgument(0);
            if (r.getRatingId() == null) { r.setRatingId((long) stored.size() + 1); stored.add(r); }
            return r;
        });
        when(ratings.overall(anyLong())).thenAnswer(inv -> {
            var visible = stored.stream().filter(r -> Rating.VISIBLE.equals(r.getStatus())
                    && r.getServiceProviderId().equals(inv.getArgument(0))).toList();
            double avg = visible.stream().mapToInt(Rating::getRating).average().orElse(0);
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{visible.isEmpty() ? null : avg, (long) visible.size()});
            return rows;
        });
    }

    @Test void ratesTheOnlyProviderAndCopiesTheOption() {
        var view = service.rate(40L, 70L, new Rate(null, 4, "  Very caring  "));

        assertThat(view.providerId()).isEqualTo(18L);
        assertThat(view.optionId()).isEqualTo(12L);
        assertThat(view.serviceTypeId()).isEqualTo(21L);
        assertThat(view.rating()).isEqualTo(4);
        assertThat(view.comment()).isEqualTo("Very caring");
        assertThat(view.status()).isEqualTo("VISIBLE");
        assertThat(provider.getTotalRatings()).isEqualTo(1);
        assertThat(provider.getAverageRating()).isEqualByComparingTo("4.00");
        verify(providers).save(provider);
    }

    @Test void secondRatingRecalculatesTheProviderAverage() {
        Rating earlier = new Rating(); earlier.setRatingId(9L); earlier.setJobId(60L); earlier.setServiceProviderId(18L);
        earlier.setRating(5); earlier.setStatus(Rating.VISIBLE); stored.add(earlier);

        service.rate(40L, 70L, new Rate(18L, 2, null));

        assertThat(provider.getTotalRatings()).isEqualTo(2);
        assertThat(provider.getAverageRating()).isEqualByComparingTo("3.50");
    }

    @Test void rejectsABookingThatIsNotCompleted() {
        job.setJobStatus("AWAITING_COMPLETION_CONFIRMATION");
        assertThatThrownBy(() -> service.rate(40L, 70L, new Rate(null, 5, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("completed");
        verify(ratings, never()).saveAndFlush(any());
    }

    @Test void rejectsASecondRatingForTheSameProvider() {
        service.rate(40L, 70L, new Rate(null, 5, null));
        assertThatThrownBy(() -> service.rate(40L, 70L, new Rate(null, 3, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already rated");
    }

    @Test void hidesAnotherCustomersBooking() {
        job.setCustomerId(99L);
        assertThatThrownBy(() -> service.rate(40L, 70L, new Rate(null, 5, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void teamBookingNeedsAProviderOnTheJob() {
        JobProviderAssignment second = new JobProviderAssignment(); second.setJobId(70L); second.setServiceProviderId(19L); second.setStatus("ACCEPTED");
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(70L)).thenReturn(List.of(second));

        assertThatThrownBy(() -> service.rate(40L, 70L, new Rate(null, 5, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("providerId is required");
        assertThatThrownBy(() -> service.rate(40L, 70L, new Rate(77L, 5, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("did not work");

        ServiceProvider other = new ServiceProvider(); other.setServiceProviderId(19L);
        when(providers.lockById(19L)).thenReturn(Optional.of(other));
        assertThat(service.rate(40L, 70L, new Rate(19L, 5, null)).providerId()).isEqualTo(19L);
    }

    @Test void jobRatingsListsWhoIsStillUnrated() {
        JobProviderAssignment second = new JobProviderAssignment(); second.setJobId(70L); second.setServiceProviderId(19L); second.setStatus("ACCEPTED");
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(70L)).thenReturn(List.of(second));
        Rating done = new Rating(); done.setRatingId(1L); done.setJobId(70L); done.setServiceProviderId(18L); done.setRating(5); done.setStatus(Rating.VISIBLE);
        when(ratings.findAllByJobIdOrderByRatingIdAsc(70L)).thenReturn(List.of(done));

        var result = service.jobRatings(40L, 70L);

        assertThat(result.canRate()).isTrue();
        assertThat(result.providers()).extracting(JobProviderRating::providerId).containsExactly(18L, 19L);
        assertThat(result.providers()).extracting(JobProviderRating::rated).containsExactly(true, false);
        assertThat(result.providers().get(0).displayName()).isEqualTo("Kumari Perera");
    }

    @Test void summaryGroupsByOptionWithADistribution() {
        when(ratings.countByOptionAndScore(18L)).thenReturn(List.of(
                new Object[]{12L, 5, 3L}, new Object[]{12L, 4, 1L}, new Object[]{13L, 2, 1L}));
        ServiceOption home = new ServiceOption(); home.setOptionId(12L); home.setName("Home care");
        ServiceOption hospital = new ServiceOption(); hospital.setOptionId(13L); hospital.setName("Hospital bedside care");
        when(options.findAllById(any())).thenReturn(List.of(home, hospital));

        var summary = service.summary(18L, null);

        assertThat(summary.overall().totalRatings()).isEqualTo(5);
        assertThat(summary.overall().averageRating()).isEqualByComparingTo("4.20");
        assertThat(summary.overall().distribution()).containsEntry("5", 3).containsEntry("2", 1).containsEntry("1", 0);
        assertThat(summary.options()).extracting(OptionStats::optionId).containsExactly(12L, 13L);
        assertThat(summary.options().get(0).averageRating()).isEqualByComparingTo("4.75");
        assertThat(summary.options().get(0).optionName()).isEqualTo("Home care");

        var onlyHospital = service.summary(18L, 13L);
        assertThat(onlyHospital.options()).extracting(OptionStats::optionId).containsExactly(13L);
        assertThat(onlyHospital.overall().totalRatings()).isEqualTo(5);
    }

    @Test void summaryWithNoRatingsHasNoAverage() {
        when(ratings.countByOptionAndScore(18L)).thenReturn(List.of());
        var summary = service.summary(18L, null);
        assertThat(summary.overall().averageRating()).isNull();
        assertThat(summary.overall().totalRatings()).isZero();
        assertThat(summary.options()).isEmpty();
    }

    @Test void hidingARatingRemovesItFromTheAverageAndIsAudited() {
        service.rate(40L, 70L, new Rate(null, 1, "rude"));
        Rating saved = stored.get(0);
        when(ratings.findById(saved.getRatingId())).thenReturn(Optional.of(saved));
        when(customers.findById(4L)).thenReturn(Optional.of(customer));

        var result = service.moderate(1L, saved.getRatingId(), new Moderate("HIDDEN", "Abusive language"));

        assertThat(result.status()).isEqualTo("HIDDEN");
        assertThat(result.moderationReason()).isEqualTo("Abusive language");
        assertThat(result.customerName()).isEqualTo("Nimali Silva");
        assertThat(provider.getTotalRatings()).isZero();
        assertThat(provider.getAverageRating()).isEqualByComparingTo("0.00");
        verify(audit).record(eq(1L), eq("RATING_MODERATED"), eq("RATING"), eq(saved.getRatingId()), any(), any());
    }

    @Test void optionSummariesForSearchCandidates() {
        when(ratings.optionSummaries(eq(12L), anyCollection())).thenReturn(List.<Object[]>of(new Object[]{18L, 4.666666, 3L}));
        var map = service.optionSummaries(12L, List.of(18L, 19L));
        assertThat(map).containsOnlyKeys(18L);
        assertThat(map.get(18L).averageRating()).isEqualByComparingTo("4.67");
        assertThat(map.get(18L).totalRatings()).isEqualTo(3);
        assertThat(service.optionSummaries(12L, List.of())).isEmpty();
    }

    @Test void reviewsUseAShortCustomerName() {
        assertThat(RatingService.shortName(customer)).isEqualTo("Nimali S.");
        Customer noLast = new Customer(); noLast.setFirstName("Ravi");
        assertThat(RatingService.shortName(noLast)).isEqualTo("Ravi");
        assertThat(RatingService.shortName(null)).isEqualTo("Customer");
    }

    @Test void pageSizeIsBounded() {
        assertThatThrownBy(() -> service.reviews(18L, null, 0, 500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adminSearch(null, null, "DELETED", 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
