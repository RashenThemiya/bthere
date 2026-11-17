package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.Cancel;
import com.jobhub.dto.job.CancellationApi.Quote;
import com.jobhub.dto.job.NoShowClaimApi.*;
import com.jobhub.entity.job.*;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.finance.WalletService;
import com.jobhub.service.upload.S3PrivateDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoShowClaimServiceTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    @Mock NoShowClaimRepository claims;
    @Mock BookingRequestRepository requests;
    @Mock JobRepository jobs;
    @Mock ServiceProviderRepository providers;
    @Mock ServiceOptionRepository options;
    @Mock MarketRepository markets;
    @Mock RefundPolicyRepository policies;
    @Mock JobCancellationRepository cancellations;
    @Mock ProviderPenaltyRepository penalties;
    @Mock BookingRequestService bookingRequests;
    @Mock BookingCancellationService bookingCancellations;
    @Mock WalletService wallets;
    @Mock S3PrivateDocumentService documents;
    @Mock AuditService audit;
    @InjectMocks NoShowClaimService service;

    Job job; BookingRequest request; ServiceOption option; ServiceProvider provider; RefundPolicy policy;
    NoShowClaim saved;
    static final String PHOTO = "providers/180/documents/abc.jpg";

    @BeforeEach void setup() {
        Market market = new Market(); market.setTimezone("Asia/Colombo");
        when(markets.findById(1L)).thenReturn(Optional.of(market));
        job = new Job(); job.setJobId(70L); job.setMarketId(1L); job.setServiceOptionId(5L); job.setAssignedServiceProviderId(18L);
        job.setSchedulingModel("TIME_BASED"); job.setJobStatus("CONFIRMED"); job.setPaymentMethod("CARD"); job.setPaymentStatus("PAID");
        job.setStartDatetime(LocalDateTime.now(ZONE).minusMinutes(90)); job.setExpectedEndDatetime(LocalDateTime.now(ZONE).plusMinutes(30));
        job.setExpectedAmount(new BigDecimal("10000.00")); job.setPlatformFee(new BigDecimal("1000.00"));
        job.setProviderEarnings(new BigDecimal("9000.00")); job.setCurrencyCode("LKR");
        job.setLatitude(new BigDecimal("6.9271000")); job.setLongitude(new BigDecimal("79.8612000"));
        when(jobs.findById(70L)).thenReturn(Optional.of(job));
        when(jobs.lockPaymentJob(70L)).thenReturn(Optional.of(job));
        request = new BookingRequest(); request.setRequestId(9L); request.setJobId(70L); request.setUserId(40L);
        request.setStatus("CONFIRMED"); request.setRefundPolicyId(3L);
        when(requests.findByJobId(70L)).thenReturn(Optional.of(request));
        when(requests.lockByJobId(70L)).thenReturn(Optional.of(request));
        option = new ServiceOption(); option.setOptionId(5L); option.setSchedulingModel("TIME_BASED"); option.setNoShowClaimAfterMinutes(60);
        when(options.findById(5L)).thenReturn(Optional.of(option));
        provider = new ServiceProvider(); provider.setServiceProviderId(18L); provider.setUserId(180L); provider.setStatus("ACTIVE");
        when(providers.findByUserId(180L)).thenReturn(Optional.of(provider));
        when(providers.findById(18L)).thenReturn(Optional.of(provider));
        policy = new RefundPolicy(); policy.setPolicyId(3L); policy.setVersion(1);
        policy.setCustomerNoShowRefundPercentage(BigDecimal.ZERO); policy.setRetainedProviderSharePercentage(new BigDecimal("70.00"));
        when(policies.findById(3L)).thenReturn(Optional.of(policy));
        when(claims.findByJobId(70L)).thenReturn(Optional.empty());
        when(claims.saveAndFlush(any())).thenAnswer(inv -> { NoShowClaim c = inv.getArgument(0); c.setClaimId(1L); saved = c; return c; });
        when(claims.lockById(1L)).thenAnswer(inv -> Optional.ofNullable(saved));
        when(documents.providerPhotoUploadedAt(180L, PHOTO)).thenReturn(Instant.now().minusSeconds(60));
        when(bookingCancellations.settle(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            JobCancellation row = new JobCancellation(); row.setCancellationId(500L);
            row.setCancelledBy(((Quote) inv.getArgument(2)).cancelledBy()); return row; });
        when(cancellations.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(penalties.saveAndFlush(any())).thenAnswer(inv -> { ProviderPenalty p = inv.getArgument(0); p.setPenaltyId(77L); return p; });
    }

    private Submit submit(String lat, String lng) {
        return new Submit(PHOTO, new BigDecimal(lat), new BigDecimal(lng), 12, Instant.now(), 3, "Called 3 times, gate locked");
    }

    private Claim file() { return service.submit(180L, 70L, submit("6.9272000", "79.8613000")); }

    // ---------- eligibility ----------

    @Test void eligibleAfterWaitingPeriodWithWindow() {
        var e = service.eligibility(180L, 70L);
        assertThat(e.eligible()).isTrue();
        assertThat(e.reason()).isNull();
        assertThat(e.opensAt()).isEqualTo(job.getStartDatetime().plusMinutes(60));
        assertThat(e.closesAt()).isEqualTo(job.getExpectedEndDatetime().plusHours(24));
        assertThat(e.maxDistanceMeters()).isEqualTo(500);
        assertThat(e.photoMaxAgeMinutes()).isEqualTo(15);
    }

    @Test void tooEarlyBeforeWaitingPeriod() {
        option.setNoShowClaimAfterMinutes(120);
        assertThat(service.eligibility(180L, 70L).reason()).isEqualTo("TOO_EARLY");
        assertThatThrownBy(this::file).isInstanceOf(ConflictException.class).hasMessageStartingWith("A complaint can be filed from");
    }

    @Test void tooLateAfterDeadline() {
        job.setStartDatetime(LocalDateTime.now(ZONE).minusHours(30)); job.setExpectedEndDatetime(LocalDateTime.now(ZONE).minusHours(28));
        option.setNoShowClaimDeadlineHours(24);
        assertThat(service.eligibility(180L, 70L).reason()).isEqualTo("TOO_LATE");
    }

    @Test void offWhenNotConfiguredOrNotTimeBased() {
        option.setNoShowClaimAfterMinutes(null);
        assertThat(service.eligibility(180L, 70L).reason()).isEqualTo("NOT_ENABLED");
        option.setNoShowClaimAfterMinutes(60); job.setSchedulingModel("ROUTE_BASED");
        assertThat(service.eligibility(180L, 70L).reason()).isEqualTo("NOT_TIME_BASED");
    }

    @Test void startedOrUnpaidBookingsAreNotEligible() {
        job.setPaymentStatus("PENDING");
        assertThat(service.eligibility(180L, 70L).reason()).isEqualTo("NOT_PAID");
        job.setPaymentStatus("PAID"); job.setJobStatus("IN_PROGRESS"); request.setStatus("IN_PROGRESS");
        assertThat(service.eligibility(180L, 70L).reason()).isEqualTo("NOT_WAITING_TO_START");
    }

    @Test void otherProviderGets404() {
        provider.setServiceProviderId(99L);
        assertThatThrownBy(() -> service.eligibility(180L, 70L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- submit ----------

    @Test void submitHoldsBookingForReviewWithEvidenceAndNoPenalty() {
        var claim = file();
        assertThat(claim.status()).isEqualTo("PENDING");
        assertThat(claim.minutesAfterStart()).isBetween(89L, 91L);
        assertThat(claim.distanceMeters()).isBetween(10, 20);
        assertThat(claim.distanceFlagged()).isFalse();
        assertThat(claim.callAttempts()).isEqualTo(3);
        verify(bookingRequests).markNoShowReview(request, job);
        verify(bookingRequests).notifyUser(request, 40L, "NO_SHOW_REPORTED");
        verifyNoInteractions(penalties, wallets);
    }

    @Test void farAwayComplaintIsFlaggedNotBlocked() {
        var claim = service.submit(180L, 70L, submit("6.9500000", "79.8612000"));
        assertThat(claim.distanceMeters()).isGreaterThan(2000);
        assertThat(claim.distanceFlagged()).isTrue();
    }

    @Test void oldPhotoIsRejected() {
        when(documents.providerPhotoUploadedAt(180L, PHOTO)).thenReturn(Instant.now().minusSeconds(20 * 60));
        assertThatThrownBy(this::file).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("within the last 15 minutes");
        when(documents.providerPhotoUploadedAt(180L, PHOTO)).thenReturn(Instant.now().minusSeconds(95 * 60));
        option.setNoShowPhotoMaxAgeMinutes(240);
        assertThatThrownBy(this::file).isInstanceOf(IllegalArgumentException.class); // taken before the booked start
        verify(bookingRequests, never()).markNoShowReview(any(), any());
    }

    // ---------- customer ----------

    @Test void customerRespondsOnceAndSeesNoEvidenceLocation() {
        file();
        when(claims.findByJobId(70L)).thenReturn(Optional.of(saved));
        var view = service.respond(40L, 70L, new CustomerResponse("I was home, nobody called"));
        assertThat(view.customerNote()).isEqualTo("I was home, nobody called");
        assertThat(view.latitude()).isNull();
        assertThat(view.photoKey()).isNull();
        verify(bookingRequests).notifyUser(request, 180L, "NO_SHOW_CUSTOMER_RESPONDED");
        assertThatThrownBy(() -> service.respond(40L, 70L, new CustomerResponse("again"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.customerClaim(41L, 70L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- admin ----------

    private void inReview() { file(); request.setStatus("NO_SHOW_REVIEW"); job.setJobStatus("NO_SHOW_REVIEW"); }

    @Test void approveChargesCustomerAndPaysProviderShareFromPolicy() {
        inReview();
        var claim = service.approve(1L, 1L, new Approve("Photo shows locked gate at booked time", null, null));
        var quote = ArgumentCaptor.forClass(Quote.class);
        verify(bookingRequests).cancelConfirmed(request, null, null);
        verify(bookingCancellations).settle(eq(request), eq(job), quote.capture(), eq(1L), any(Cancel.class));
        assertThat(quote.getValue().refundAmount()).isEqualByComparingTo("0");
        assertThat(quote.getValue().providerAmount()).isEqualByComparingTo("7000");
        assertThat(quote.getValue().platformAmount()).isEqualByComparingTo("3000");
        assertThat(claim.status()).isEqualTo("APPROVED");
        assertThat(claim.providerAmount()).isEqualByComparingTo("7000");
        verify(bookingRequests).notifyUser(request, 180L, "NO_SHOW_CLAIM_APPROVED");
        assertThatThrownBy(() -> service.approve(1L, 1L, new Approve("again", null, null))).isInstanceOf(ConflictException.class);
    }

    @Test void approveOverridesAndRequiresAPercentage() {
        inReview();
        policy.setCustomerNoShowRefundPercentage(null);
        assertThatThrownBy(() -> service.approve(1L, 1L, new Approve("ok", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("customerNoShowRefundPercentage");
        var claim = service.approve(1L, 1L, new Approve("ok", new BigDecimal("50"), new BigDecimal("100")));
        // 50% of (10000 - 1000 platform fee) = 4500 refund; kept 5500 all to the provider.
        assertThat(claim.refundAmount()).isEqualByComparingTo("4500");
        assertThat(claim.providerAmount()).isEqualByComparingTo("5500");
        assertThat(claim.platformAmount()).isEqualByComparingTo("0");
    }

    @Test void rejectRefundsInFullWithoutPenalty() {
        inReview();
        var claim = service.reject(1L, 1L, new Reject("Customer was home; provider went to the wrong address", null));
        assertThat(claim.status()).isEqualTo("REJECTED");
        assertThat(claim.refundAmount()).isEqualByComparingTo("10000");
        assertThat(claim.providerAmount()).isEqualByComparingTo("0");
        verifyNoInteractions(penalties);
        verify(bookingRequests).notifyUser(request, 180L, "NO_SHOW_CLAIM_REJECTED");
    }

    @Test void rejectWithPenaltyChargesProviderAndCountsAsProviderCancellation() {
        inReview();
        var claim = service.reject(1L, 1L, new Reject("False complaint", new BigDecimal("1500")));
        var penalty = ArgumentCaptor.forClass(ProviderPenalty.class);
        verify(penalties).saveAndFlush(penalty.capture());
        assertThat(penalty.getValue().getPenaltyAmount()).isEqualByComparingTo("1500");
        assertThat(penalty.getValue().getProviderUserId()).isEqualTo(180L);
        verify(wallets).collectProviderPenalty(77L, 1L);
        var row = ArgumentCaptor.forClass(JobCancellation.class);
        verify(cancellations, atLeastOnce()).saveAndFlush(row.capture());
        assertThat(row.getValue().getCancelledBy()).isEqualTo("PROVIDER");
        assertThat(row.getValue().getCancelledByUserId()).isEqualTo(180L);
        assertThat(claim.penaltyAmount()).isEqualByComparingTo("1500");
    }

    @Test void penaltyCannotExceedBookingAmount() {
        inReview();
        assertThatThrownBy(() -> service.reject(1L, 1L, new Reject("x", new BigDecimal("10000.01"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bookingRequests, never()).cancelConfirmed(any(), any(), any());
    }

    // ---------- settings ----------

    @Test void settingsOnlyEnableForTimeBasedOptions() {
        var updated = service.updateSettings(1L, 5L, new Settings(null, null, false, 90, 48, 300, 10));
        assertThat(updated.enabled()).isTrue();
        assertThat(option.getNoShowClaimAfterMinutes()).isEqualTo(90);
        option.setSchedulingModel("ROUTE_BASED");
        assertThatThrownBy(() -> service.updateSettings(1L, 5L, new Settings(null, null, false, 90, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.updateSettings(1L, 5L, new Settings(null, null, true, null, null, null, null)).enabled()).isFalse();
    }
}
