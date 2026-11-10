package com.jobhub.service.customer;

import com.jobhub.dto.customer.WalletApi;
import com.jobhub.dto.job.CancellationApi.Cancel;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCancellation;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.entity.market.RefundPolicyTier;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.finance.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingCancellationServiceTest {
    @Mock BookingRequestRepository requests;
    @Mock JobRepository jobs;
    @Mock ServiceProviderRepository providers;
    @Mock MarketRepository markets;
    @Mock RefundPolicyRepository policies;
    @Mock JobCancellationRepository cancellations;
    @Mock WalletService wallets;
    @Mock BookingRequestService bookingRequests;
    @Mock AuditService audit;
    @InjectMocks BookingCancellationService service;

    BookingRequest request;
    Job job;
    RefundPolicy policy;

    @BeforeEach void setup() {
        request = new BookingRequest(); request.setRequestId(9L); request.setUserId(5L); request.setJobId(70L);
        request.setStatus("CONFIRMED"); request.setRefundPolicyId(3L);
        job = new Job(); job.setJobId(70L); job.setMarketId(1L); job.setCurrencyCode("LKR"); job.setJobStatus("CONFIRMED");
        job.setPaymentMethod("CARD"); job.setPaymentStatus("PAID"); job.setAssignedServiceProviderId(15L);
        job.setExpectedAmount(new BigDecimal("10000.00")); job.setPlatformFee(new BigDecimal("1500.00"));
        job.setProviderEarnings(new BigDecimal("8500.00"));
        job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        policy = new RefundPolicy(); policy.setPolicyId(3L); policy.setVersion(1); policy.setPlatformFeeRefundable(true);
        policy.setRetainedProviderSharePercentage(new BigDecimal("70"));
        policy.replaceTiers(List.of(tier("Early", 1440, "100"), tier("Two hours", 120, "50"), tier("Late", 0, "0")));
        var market = new Market(); market.setMarketId(1L); market.setTimezone("UTC");
        var provider = new ServiceProvider(); provider.setServiceProviderId(15L); provider.setUserId(9L);
        when(requests.lockByJobId(70L)).thenReturn(Optional.of(request));
        when(requests.findByJobId(70L)).thenReturn(Optional.of(request));
        when(jobs.lockPaymentJob(70L)).thenReturn(Optional.of(job));
        when(jobs.findById(70L)).thenReturn(Optional.of(job));
        when(markets.findById(1L)).thenReturn(Optional.of(market));
        when(policies.findById(3L)).thenReturn(Optional.of(policy));
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        when(providers.findByUserId(9L)).thenReturn(Optional.of(provider));
        when(cancellations.findByJobId(70L)).thenReturn(Optional.empty());
        when(cancellations.saveAndFlush(any())).thenAnswer(i -> { JobCancellation c = i.getArgument(0); c.setCancellationId(1L); return c; });
        when(wallets.refundCancelledRequestBooking(anyLong(), anyLong(), any(), any(), any(), anyLong()))
                .thenReturn(new WalletApi.Operation(88L, "CARD_BOOKING_CANCEL", 70L, "LKR", new BigDecimal("10000"), "POSTED",
                        null, null, null, "PAYHERE", "receipt"));
    }

    private RefundPolicyTier tier(String name, int minutes, String percentage) {
        var tier = new RefundPolicyTier(); tier.setName(name); tier.setMinimumMinutesBeforeStart(minutes);
        tier.setRefundPercentage(new BigDecimal(percentage)); tier.setCancellationFee(BigDecimal.ZERO); tier.setPriority(minutes);
        return tier;
    }

    @Test void customerPreviewUsesSnapshotPolicyAndTier() {
        var quote = service.customerPreview(5L, 70L);
        assertThat(quote.refundTierName()).isEqualTo("Two hours");
        assertThat(quote.refundAmount()).isEqualByComparingTo("5000");
        assertThat(quote.providerAmount()).isEqualByComparingTo("3500");
        assertThat(quote.platformAmount()).isEqualByComparingTo("1500");
        verifyNoInteractions(wallets);
    }

    @Test void customerCancelStopsBookingMovesMoneyAndRecordsSnapshot() {
        var result = service.cancelByCustomer(5L, 70L, new Cancel("Plans changed", null, new BigDecimal("5000.00")));
        verify(bookingRequests).cancelConfirmed(request, 9L, "BOOKING_CANCELLED_BY_CUSTOMER");
        verify(wallets).refundCancelledRequestBooking(eq(5L), eq(70L), argThat(v -> v.compareTo(new BigDecimal("5000")) == 0),
                argThat(v -> v.compareTo(new BigDecimal("3500")) == 0), argThat(v -> v.compareTo(new BigDecimal("1500")) == 0), eq(5L));
        assertThat(result.cancelledBy()).isEqualTo("CUSTOMER");
        assertThat(result.refundPolicyId()).isEqualTo(3L);
        assertThat(result.walletOperationId()).isEqualTo(88L);
        assertThat(result.refundDestination()).isEqualTo("WALLET");
    }

    @Test void changedAmountMissingPolicyAndWrongStateAreRefused() {
        assertThatThrownBy(() -> service.cancelByCustomer(5L, 70L, new Cancel("x", null, new BigDecimal("9999"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("refund amount changed");
        request.setRefundPolicyId(null);
        when(bookingRequests.currentRefundPolicyId(job)).thenReturn(null);
        assertThatThrownBy(() -> service.cancelByCustomer(5L, 70L, new Cancel("x", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("No refund policy");
        request.setRefundPolicyId(3L); request.setStatus("IN_PROGRESS"); job.setJobStatus("IN_PROGRESS");
        assertThatThrownBy(() -> service.cancelByCustomer(5L, 70L, new Cancel("x", null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("has not started");
        request.setStatus("CONFIRMED"); job.setJobStatus("CONFIRMED"); job.setPaymentStatus("PENDING");
        assertThatThrownBy(() -> service.cancelByCustomer(5L, 70L, new Cancel("x", null, null)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(wallets);
        verify(bookingRequests, never()).cancelConfirmed(any(), any(), any());
    }

    @Test void otherCustomersAndRepeatCancelsAreHandled() {
        assertThatThrownBy(() -> service.cancelByCustomer(6L, 70L, new Cancel("x", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        var saved = new JobCancellation(); saved.setCancellationId(4L); saved.setJobId(70L); saved.setCancelledBy("CUSTOMER");
        when(cancellations.findByJobId(70L)).thenReturn(Optional.of(saved));
        assertThat(service.cancelByCustomer(5L, 70L, new Cancel("x", null, null)).cancellationId()).isEqualTo(4L);
        verifyNoInteractions(wallets);
    }

    @Test void providerCancelRefundsCustomerInFullWithoutPolicyRequirement() {
        request.setRefundPolicyId(null);
        var result = service.cancelByProvider(9L, 70L, new Cancel("Vehicle broke down", "Sorry", null));
        verify(bookingRequests).cancelConfirmed(request, null, null);
        verify(wallets).refundCancelledRequestBooking(eq(5L), eq(70L), argThat(v -> v.compareTo(new BigDecimal("10000")) == 0),
                argThat(v -> v.signum() == 0), argThat(v -> v.signum() == 0), eq(9L));
        assertThat(result.cancelledBy()).isEqualTo("PROVIDER");
        assertThat(result.refundPercentage()).isEqualByComparingTo("100");
    }

    @Test void onlyTheAssignedProviderCanCancel() {
        var other = new ServiceProvider(); other.setServiceProviderId(16L); other.setUserId(10L);
        when(providers.findByUserId(10L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.cancelByProvider(10L, 70L, new Cancel("x", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(wallets);
    }
}
