package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.AutoCancelUpdate;
import com.jobhub.dto.job.CancellationApi.Cancel;
import com.jobhub.dto.job.CancellationApi.Quote;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCancellation;
import com.jobhub.entity.job.JobProviderTracking;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobProviderTrackingRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingAutoCancelServiceTest {
    @Mock BookingRequestRepository requests;
    @Mock JobRepository jobs;
    @Mock JobCancellationRepository cancellations;
    @Mock JobProviderTrackingRepository trackings;
    @Mock ServiceProviderRepository providers;
    @Mock ServiceOptionRepository options;
    @Mock MarketRepository markets;
    @Mock RefundPolicyRepository policies;
    @Mock BookingRequestService bookingRequests;
    @Mock BookingCancellationService bookingCancellations;
    @Mock AuditService audit;

    AutoCancelRules rules;
    BookingAutoCancelService service;
    BookingRequest request;
    Job job;
    ServiceOption option;

    @BeforeEach
    void setup() {
        rules = new AutoCancelRules(requests, cancellations, trackings, options, markets);
        org.springframework.test.util.ReflectionTestUtils.setField(rules, "appliesFrom", Instant.EPOCH);
        service = new BookingAutoCancelService(requests, jobs, cancellations, providers, options, markets, policies,
                rules, bookingRequests, bookingCancellations, audit);
        var market = new Market(); market.setMarketId(1L); market.setTimezone("UTC");
        when(markets.findById(1L)).thenReturn(Optional.of(market));
        option = new ServiceOption(); option.setOptionId(4L);
        when(options.findById(4L)).thenReturn(Optional.of(option));
        job = new Job(); job.setJobId(70L); job.setMarketId(1L); job.setServiceOptionId(4L); job.setJobStatus("CONFIRMED");
        job.setPaymentMethod("CARD"); job.setPaymentStatus("PAID"); job.setCurrencyCode("LKR");
        job.setExpectedAmount(new BigDecimal("5000.00")); job.setPlatformFee(new BigDecimal("500.00"));
        job.setAssignedServiceProviderId(8L);
        startedMinutesAgo(61);
        request = new BookingRequest(); request.setRequestId(106L); request.setUserId(5L); request.setJobId(70L);
        request.setStatus("CONFIRMED");
        request.setCodeExpiresAt(Instant.now().plus(Duration.ofHours(25)));
        when(requests.lockByJobId(70L)).thenReturn(Optional.of(request));
        when(requests.findByJobId(70L)).thenReturn(Optional.of(request));
        when(jobs.lockPaymentJob(70L)).thenReturn(Optional.of(job));
        when(cancellations.findByJobId(70L)).thenReturn(Optional.empty());
        when(trackings.findAllByJobId(70L)).thenReturn(List.of());
        var provider = new ServiceProvider(); provider.setServiceProviderId(8L); provider.setUserId(9L);
        when(providers.findById(8L)).thenReturn(Optional.of(provider));
        var row = new JobCancellation(); row.setCancellationId(3L);
        when(bookingCancellations.settle(any(), any(), any(), any(), any())).thenReturn(row);
    }

    @Test
    void unstartedPaidBookingIsCancelledAfterTheGraceWithAFullRefundAndNoFee() {
        assertThat(service.cancelIfDue(70L)).isTrue();

        verify(bookingRequests).autoCancelConfirmed(request, 9L);
        var quote = ArgumentCaptor.forClass(Quote.class);
        var input = ArgumentCaptor.forClass(Cancel.class);
        verify(bookingCancellations).settle(eq(request), eq(job), quote.capture(), isNull(), input.capture());
        assertThat(quote.getValue().cancelledBy()).isEqualTo("SYSTEM");
        assertThat(quote.getValue().refundAmount()).isEqualByComparingTo("5000.00");
        assertThat(quote.getValue().cancellationFee()).isEqualByComparingTo("0");
        assertThat(quote.getValue().providerAmount()).isEqualByComparingTo("0");
        assertThat(quote.getValue().platformAmount()).isEqualByComparingTo("0");
        assertThat(quote.getValue().refundDestination()).isEqualTo("WALLET");
        assertThat(input.getValue().reason()).isEqualTo("PROVIDER_DID_NOT_START");
        verify(audit).record(isNull(), eq("BOOKING_AUTO_CANCELLED"), eq("JOB"), eq(70L), isNull(), any());
    }

    @Test
    void walletReservedBookingsAreCoveredToo() {
        job.setPaymentMethod("WALLET"); job.setPaymentStatus("RESERVED");
        assertThat(service.cancelIfDue(70L)).isTrue();
    }

    @Test
    void notCancelledBeforeTheGraceEnds() {
        startedMinutesAgo(59);
        assertThat(service.cancelIfDue(70L)).isFalse();
        verifyNoInteractions(bookingCancellations);
        verify(bookingRequests, never()).autoCancelConfirmed(any(), any());
    }

    @Test
    void perOptionGraceOverridesTheDefault() {
        option.setAutoCancelAfterMinutes(15);
        startedMinutesAgo(16);
        assertThat(service.cancelIfDue(70L)).isTrue();
    }

    @Test
    void startedNoShowReviewUnpaidAndAlreadyCancelledBookingsAreNeverAutoCancelled() {
        for (String status : List.of("IN_PROGRESS", "NO_SHOW_REVIEW", "AWAITING_COMPLETION_CONFIRMATION", "COMPLETED", "CANCELLED")) {
            job.setJobStatus(status); request.setStatus(status);
            assertThat(service.cancelIfDue(70L)).as(status).isFalse();
        }
        job.setJobStatus("CONFIRMED"); request.setStatus("CONFIRMED");
        job.setActualStartDatetime(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(service.cancelIfDue(70L)).isFalse();
        job.setActualStartDatetime(null); job.setPaymentStatus("PENDING");
        assertThat(service.cancelIfDue(70L)).isFalse();
        job.setPaymentStatus("PAID");
        when(cancellations.findByJobId(70L)).thenReturn(Optional.of(new JobCancellation()));
        assertThat(service.cancelIfDue(70L)).isFalse();
        verifyNoInteractions(bookingCancellations);
    }

    @Test
    void providerWhoArrivedHoldsTheBookingUntilTheServiceWindowCloses() {
        var tracking = new JobProviderTracking(); tracking.setArrivedAt(Instant.now().minus(Duration.ofMinutes(70)));
        when(trackings.findAllByJobId(70L)).thenReturn(List.of(tracking));
        var plan = rules.plan(job, request);
        assertThat(plan.hold()).isEqualTo("PROVIDER_ARRIVED");
        assertThat(plan.autoCancelAt()).isEqualTo(request.getCodeExpiresAt());
        assertThat(service.cancelIfDue(70L)).isFalse();

        request.setCodeExpiresAt(Instant.now().minusSeconds(1));
        assertThat(service.cancelIfDue(70L)).isTrue();
        var input = ArgumentCaptor.forClass(Cancel.class);
        verify(bookingCancellations).settle(any(), any(), any(), isNull(), input.capture());
        assertThat(input.getValue().reason()).isEqualTo("NOT_STARTED_IN_SERVICE_WINDOW");
    }

    @Test
    void aTriedStartCodeHoldsTooAndTheNoShowComplaintWindowIsKeptOpen() {
        request.setStartAttempts(2);
        request.setCodeExpiresAt(Instant.now().minusSeconds(1));
        option.setNoShowClaimAfterMinutes(30);
        job.setExpectedEndDatetime(job.getStartDatetime().plusHours(2));
        var plan = rules.plan(job, request);
        assertThat(plan.hold()).isEqualTo("START_CODE_TRIED");
        // complaint window: end (start + 2h) + 24h
        assertThat(plan.autoCancelAt()).isEqualTo(job.getStartDatetime().plusHours(26).toInstant(ZoneOffset.UTC));
        assertThat(service.cancelIfDue(70L)).isFalse();
    }

    @Test
    void bookingResponsesShowTheAutoCancelTimeOrTheCancellation() {
        var info = rules.info(job);
        assertThat(info.autoCancelAt()).isEqualTo(job.getStartDatetime().plusMinutes(60).toInstant(ZoneOffset.UTC));
        assertThat(info.autoCancelHold()).isNull();
        assertThat(info.cancellation()).isNull();

        job.setJobStatus("CANCELLED");
        var row = new JobCancellation(); row.setCancelledBy("SYSTEM"); row.setReason("PROVIDER_DID_NOT_START");
        row.setRefundAmount(new BigDecimal("5000.00")); row.setRefundDestination("WALLET");
        row.setCreatedAt(LocalDateTime.now());
        when(cancellations.findByJobId(70L)).thenReturn(Optional.of(row));
        info = rules.info(job);
        assertThat(info.autoCancelAt()).isNull();
        assertThat(info.cancellation().automatic()).isTrue();
        assertThat(info.cancellation().reason()).isEqualTo("PROVIDER_DID_NOT_START");
        assertThat(info.cancellation().refundDestination()).isEqualTo("WALLET");
        assertThat(rules.autoCancelled(70L)).isTrue();
    }

    @Test
    void bookingsThatStartedBeforeTheFeatureAreLeftAlone() {
        org.springframework.test.util.ReflectionTestUtils.setField(rules, "appliesFrom", Instant.now().minus(Duration.ofMinutes(30)));
        assertThat(rules.plan(job, request)).isNull();
        assertThat(service.cancelIfDue(70L)).isFalse();
        verifyNoInteractions(bookingCancellations);
    }

    @Test
    void settingsFallBackToTheDefault() {
        assertThat(service.settings(4L).effectiveMinutes()).isEqualTo(60);
        var updated = service.updateSettings(1L, 4L, new AutoCancelUpdate(45));
        assertThat(updated.autoCancelAfterMinutes()).isEqualTo(45);
        assertThat(updated.effectiveMinutes()).isEqualTo(45);
        assertThat(service.updateSettings(1L, 4L, new AutoCancelUpdate(null)).effectiveMinutes()).isEqualTo(60);
    }

    private void startedMinutesAgo(long minutes) {
        job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(minutes));
    }
}
