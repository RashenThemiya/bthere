package com.jobhub.service.customer;

import com.jobhub.entity.auth.User;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobProviderAssignment;
import com.jobhub.entity.job.Rating;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.job.RatingRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingProviderServiceTest {
    @Mock JobRepository jobs;
    @Mock CustomerRepository customers;
    @Mock JobProviderAssignmentRepository jobAssignments;
    @Mock ServiceProviderRepository providers;
    @Mock UserRepository users;
    @Mock RatingRepository ratings;
    @InjectMocks BookingProviderService service;

    Job job;

    @BeforeEach void setup() {
        Customer customer = new Customer(); customer.setCustomerId(4L); customer.setUserId(40L);
        when(customers.findByUserId(40L)).thenReturn(Optional.of(customer));
        job = new Job(); job.setJobId(70L); job.setCustomerId(4L); job.setAssignedServiceProviderId(18L);
        job.setFulfillmentModel("ONE_TO_ONE"); job.setJobStatus("CONFIRMED");
        when(jobs.findById(70L)).thenReturn(Optional.of(job));
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(70L)).thenReturn(List.of());
        provider(18L, 180L, "Kumari", "Perera", "+94771234567");
        when(ratings.findByJobIdAndServiceProviderId(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    private void provider(Long id, Long userId, String first, String last, String phone) {
        ServiceProvider p = new ServiceProvider(); p.setServiceProviderId(id); p.setUserId(userId);
        p.setFirstName(first); p.setLastName(last); p.setProfilePhoto("https://cdn.example/" + id + ".jpg");
        p.setAverageRating(new BigDecimal("4.50")); p.setTotalRatings(12); p.setStatus("ACTIVE");
        when(providers.findById(id)).thenReturn(Optional.of(p));
        User u = new User(); u.setUserId(userId); u.setPhoneNumber(phone); u.setStatus("ACTIVE");
        when(users.findById(userId)).thenReturn(Optional.of(u));
    }

    private static JobProviderAssignment assignment(Long providerId, String status) {
        JobProviderAssignment a = new JobProviderAssignment(); a.setJobId(70L); a.setServiceProviderId(providerId); a.setStatus(status);
        return a;
    }

    @Test void confirmedBookingReturnsDetailsAndCallablePhone() {
        var view = service.providers(40L, 70L);
        assertThat(view.jobStatus()).isEqualTo("CONFIRMED");
        assertThat(view.contactAllowedStatuses()).containsExactly("CONFIRMED", "IN_PROGRESS");
        var p = view.providers().get(0);
        assertThat(p.providerId()).isEqualTo(18L);
        assertThat(p.role()).isEqualTo("PRIMARY");
        assertThat(p.displayName()).isEqualTo("Kumari Perera");
        assertThat(p.profilePhoto()).isEqualTo("https://cdn.example/18.jpg");
        assertThat(p.averageRating()).isEqualByComparingTo("4.5");
        assertThat(p.totalRatings()).isEqualTo(12);
        assertThat(p.contact().callAllowed()).isTrue();
        assertThat(p.contact().phoneNumber()).isEqualTo("+94771234567");
        assertThat(p.contact().unavailableReason()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"COMPLETED", "CANCELLED", "AWAITING_COMPLETION_CONFIRMATION"})
    void historyKeepsNameAndPhotoButHidesPhone(String status) {
        job.setJobStatus(status);
        var p = service.providers(40L, 70L).providers().get(0);
        assertThat(p.displayName()).isEqualTo("Kumari Perera");
        assertThat(p.profilePhoto()).isNotNull();
        assertThat(p.contact().callAllowed()).isFalse();
        assertThat(p.contact().phoneNumber()).isNull();
        assertThat(p.contact().unavailableReason()).isEqualTo("BOOKING_ENDED");
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "AWAITING_PAYMENT", "PAYMENT_PENDING"})
    void unpaidBookingHidesPhone(String status) {
        job.setJobStatus(status);
        var contact = service.providers(40L, 70L).providers().get(0).contact();
        assertThat(contact.callAllowed()).isFalse();
        assertThat(contact.phoneNumber()).isNull();
        assertThat(contact.unavailableReason()).isEqualTo("BOOKING_NOT_CONFIRMED");
    }

    @Test void inProgressWithoutPhoneReportsPhoneNotAvailable() {
        job.setJobStatus("IN_PROGRESS");
        provider(18L, 180L, "Kumari", "Perera", null);
        var contact = service.providers(40L, 70L).providers().get(0).contact();
        assertThat(contact.callAllowed()).isFalse();
        assertThat(contact.unavailableReason()).isEqualTo("PHONE_NOT_AVAILABLE");
    }

    @Test void teamBookingListsPrimaryThenActiveMembersOnly() {
        job.setFulfillmentModel("TEAM");
        provider(19L, 190L, "Saman", "Fernando", "+94770000019");
        provider(20L, 200L, "Removed", "Member", "+94770000020");
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(70L)).thenReturn(List.of(
                assignment(18L, "ASSIGNED"), assignment(19L, "ASSIGNED"), assignment(20L, "REMOVED")));
        when(ratings.findByJobIdAndServiceProviderId(70L, 19L)).thenReturn(Optional.of(new Rating()));
        var list = service.providers(40L, 70L).providers();
        assertThat(list).extracting(p -> p.providerId()).containsExactly(18L, 19L);
        assertThat(list).extracting(p -> p.role()).containsExactly("PRIMARY", "TEAM_MEMBER");
        assertThat(list.get(1).ratedByYou()).isTrue();
        assertThat(list.get(1).contact().phoneNumber()).isEqualTo("+94770000019");
    }

    @Test void otherCustomersBookingIsNotFound() {
        job.setCustomerId(99L);
        assertThatThrownBy(() -> service.providers(40L, 70L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test void missingBookingIsNotFound() {
        assertThatThrownBy(() -> service.providers(40L, 71L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
