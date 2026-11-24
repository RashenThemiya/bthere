package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCancellation;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileOverviewServiceTest {
    @Mock UserRepository users;
    @Mock CustomerRepository customers;
    @Mock JobRepository jobs;
    @Mock BookingRequestRepository requests;
    @Mock JobCancellationRepository cancellations;
    @Mock ServiceProviderTypeRepository serviceTypes;
    @Mock ServiceOptionRepository options;
    @Mock com.jobhub.repository.market.MarketRepository markets;
    @Mock CustomerRequirementsService requirements;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @org.mockito.Spy com.jobhub.service.upload.ProfilePhotoUrls photoUrls =
            new com.jobhub.service.upload.ProfilePhotoUrls("jobhube", "eu-north-1", "https://jobhube.s3.eu-north-1.amazonaws.com", "https://api.test");
    @InjectMocks ProfileOverviewService service;

    List<Job> jobList = new ArrayList<>();
    List<BookingRequest> requestList = new ArrayList<>();
    long seq = 0;

    @BeforeEach void setup() {
        User u = new User(); u.setUserId(40L); u.setEmail("madu@example.com"); u.setEmailVerified(true);
        u.setPhoneNumber("+94771234567"); u.setPhoneVerified(false);
        when(users.findById(40L)).thenReturn(Optional.of(u));
        Customer c = new Customer(); c.setCustomerId(4L); c.setUserId(40L); c.setFirstName("Madu"); c.setLastName("Perera");
        c.setProfilePhoto("https://cdn.example/madu.jpg"); c.setStatus("ACTIVE");
        when(customers.findByUserId(40L)).thenReturn(Optional.of(c));
        when(jobs.findAllByCustomerIdOrderByCreatedAtDesc(4L)).thenReturn(jobList);
        when(requests.findAllByJobIdIn(any())).thenAnswer(inv -> requestList.stream().filter(r -> r.getJobId() != null).toList());
        when(requests.findAllByUserIdAndStatusOrderByCreatedAtDesc(40L, "MATCHING"))
                .thenAnswer(inv -> requestList.stream().filter(r -> "MATCHING".equals(r.getStatus())).toList());
        when(cancellations.findByJobId(anyLong())).thenReturn(Optional.empty());
        ServiceProviderType caregiver = new ServiceProviderType(); caregiver.setServiceProviderTypeId(1L); caregiver.setName("Caregiver");
        when(serviceTypes.findById(1L)).thenReturn(Optional.of(caregiver));
        ServiceOption hospital = new ServiceOption(); hospital.setOptionId(1L); hospital.setServiceProviderTypeId(1L); hospital.setName("Hospital Care");
        when(options.findById(1L)).thenReturn(Optional.of(hospital));
        when(requirements.profileDocuments(40L)).thenReturn(List.of());
    }

    private Job job(String status, String payment, String amount, String funding) {
        Job j = new Job(); j.setJobId(++seq); j.setCustomerId(4L); j.setServiceProviderTypeId(1L); j.setServiceOptionId(1L);
        j.setJobStatus(status); j.setPaymentStatus(payment); j.setCurrencyCode("LKR");
        j.setExpectedAmount(amount == null ? null : new BigDecimal(amount));
        j.setStartDatetime(LocalDateTime.of(2026, 11, 20, 9, 0)); j.setExpectedEndDatetime(LocalDateTime.of(2026, 11, 20, 11, 0));
        j.setCreatedAt(LocalDateTime.of(2026, 11, 1, 0, 0).plusHours(seq));
        jobList.add(0, j);
        if (funding != null) {
            BookingRequest r = new BookingRequest(); r.setRequestId(100 + seq); r.setJobId(j.getJobId()); r.setUserId(40L);
            r.setStatus(status); r.setPaymentStatus(payment); r.setPaymentFundingMode(funding);
            requestList.add(r);
        }
        return j;
    }

    private void cancellation(Job j, String refund) {
        JobCancellation c = new JobCancellation(); c.setJobId(j.getJobId()); c.setRefundAmount(new BigDecimal(refund));
        when(cancellations.findByJobId(j.getJobId())).thenReturn(Optional.of(c));
    }

    @Test void profileAndContactsComeFromStoredRecords() {
        var o = service.overview(40L, 5);
        assertThat(o.profile().customerId()).isEqualTo(4L);
        assertThat(o.profile().displayName()).isEqualTo("Madu Perera");
        assertThat(o.profile().profilePhoto()).isEqualTo("https://cdn.example/madu.jpg");
        assertThat(o.contacts().email()).isEqualTo("madu@example.com");
        assertThat(o.contacts().emailVerified()).isTrue();
        assertThat(o.contacts().phoneVerified()).isFalse();
    }

    @Test void spendingSeparatesLiveTestRefundsReservedAndReview() {
        job("COMPLETED", "SETTLED", "7000", "PAYHERE");
        cancellation(job("CANCELLED", "PARTIALLY_REFUNDED", "4000", "PAYHERE"), "2000");
        cancellation(job("CANCELLED", "REFUNDED", "3000", "PAYHERE"), "3000");
        job("CONFIRMED", "RESERVED", "1500", "PAYHERE");          // wallet hold: reserved, not spent
        job("COMPLETED", "SETTLED", "9999", "MOCK");             // test money
        job("COMPLETED", "PAID", "500", "PAYHERE_SANDBOX");      // test money
        job("COMPLETED", "PAID_REVIEW", "800", "PAYHERE");       // under review: excluded
        job("COMPLETED", "SETTLED", "600", null);                // legacy, unknown funding: excluded
        job("CANCELLED", "NOT_STARTED", "900", "PAYHERE");       // nothing taken: ignored

        var s = service.overview(40L, 5).statistics();
        assertThat(s.spending()).hasSize(1);
        var live = s.spending().get(0);
        assertThat(live.currencyCode()).isEqualTo("LKR");
        assertThat(live.paidAmount()).isEqualByComparingTo("14000");
        assertThat(live.refundedAmount()).isEqualByComparingTo("5000");
        assertThat(live.totalSpent()).isEqualByComparingTo("9000");
        assertThat(live.reservedAmount()).isEqualByComparingTo("1500");
        assertThat(live.paidBookingCount()).isEqualTo(3);
        assertThat(s.testSpending().get(0).paidAmount()).isEqualByComparingTo("10499");
        assertThat(s.excludedPaymentCount()).isEqualTo(2);
    }

    @Test void refundedWithoutCancellationRecordIsExcludedNotGuessed() {
        job("CANCELLED", "REFUNDED", "3000", "PAYHERE");
        var s = service.overview(40L, 5).statistics();
        assertThat(s.spending()).isEmpty();
        assertThat(s.excludedPaymentCount()).isEqualTo(1);
    }

    @Test void countsAndRecentListsWithHasMore() {
        BookingRequest matching = new BookingRequest(); matching.setRequestId(900L); matching.setUserId(40L);
        matching.setStatus("MATCHING"); matching.setOptionId(1L); matching.setPaymentStatus("NOT_STARTED");
        matching.setCreatedAt(LocalDateTime.of(2026, 12, 1, 0, 0));
        matching.setPayloadJson("{\"marketId\":1,\"booking\":{\"startDatetime\":\"2026-12-02T08:00:00\",\"expectedEndDatetime\":\"2026-12-02T10:00:00\"}}");
        requestList.add(matching);
        job("CONFIRMED", "PAID", "7000", "PAYHERE");
        job("IN_PROGRESS", "PAID", "7000", "PAYHERE");
        for (int i = 0; i < 3; i++) job("COMPLETED", "SETTLED", "7000", "PAYHERE");
        job("CANCELLED", "NOT_STARTED", "7000", "PAYHERE");

        var o = service.overview(40L, 2);
        assertThat(o.statistics().awaitingProviderCount()).isEqualTo(1);
        assertThat(o.statistics().activeCount()).isEqualTo(2);
        assertThat(o.statistics().pendingCount()).isEqualTo(3);
        assertThat(o.statistics().completedCount()).isEqualTo(3);
        assertThat(o.statistics().cancelledCount()).isEqualTo(1);

        var pending = o.pendingServices();
        assertThat(pending.items()).hasSize(2);
        assertThat(pending.hasMore()).isTrue();
        assertThat(pending.totalCount()).isEqualTo(3);
        var first = pending.items().get(0);
        assertThat(first.kind()).isEqualTo("REQUEST");
        assertThat(first.requestId()).isEqualTo(900L);
        assertThat(first.jobId()).isNull();
        assertThat(first.serviceName()).isEqualTo("Caregiver");
        assertThat(first.optionName()).isEqualTo("Hospital Care");
        assertThat(first.startAt()).isEqualTo(LocalDateTime.of(2026, 12, 2, 8, 0));
        assertThat(pending.items().get(1).kind()).isEqualTo("BOOKING");

        var history = o.serviceHistory();
        assertThat(history.items()).hasSize(2);
        assertThat(history.hasMore()).isTrue();
        assertThat(history.totalCount()).isEqualTo(4);
        assertThat(history.items().get(0).status()).isEqualTo("CANCELLED");
        assertThat(history.items().get(0).amount()).isEqualByComparingTo("7000");
        assertThat(history.items().get(0).currencyCode()).isEqualTo("LKR");
    }

    @Test void documentsMapServicesAndMissingSubmissions() {
        var def = new Definition("NIC", "National identity card", null, "DOCUMENT_UPLOAD", true, "DOCUMENT_REVIEW", true,
                List.of(new Target("GLOBAL", null, null), new Target("OPTION", 1L, 1L)), List.of(), List.of(), 1, 2, 5, List.of("PDF"), 1);
        var sub = new Submission(55L, 4L, 7L, "NIC", null, "REJECTED", "REJECTED", LocalDate.of(2027, 1, 1), 1L,
                LocalDateTime.of(2026, 11, 10, 12, 0), "Photo is blurry", 1, 1);
        var missingDef = new Definition("POLICE_REPORT", "Police report", null, "FILE_UPLOAD", false, "DOCUMENT_REVIEW", false,
                List.of(new Target("GLOBAL", null, null)), List.of(), List.of(), 1, 1, 5, List.of("PDF"), 2);
        when(requirements.profileDocuments(40L)).thenReturn(List.of(
                new FormItem(new Requirement(7L, 1, "ACTIVE", def), sub),
                new FormItem(new Requirement(8L, 1, "ACTIVE", missingDef), null)));
        when(requirements.activeTarget(any())).thenReturn(true);

        var docs = service.overview(40L, 5).documents();
        assertThat(docs.get(0).actionRequired()).isTrue();
        assertThat(docs.get(0).upload().maximumFiles()).isEqualTo(2);
        assertThat(docs.get(0).upload().maximumFileSizeMb()).isEqualTo(5);
        assertThat(docs.get(0).upload().allowedContentTypes()).containsExactly("application/pdf");
        assertThat(docs.get(0).upload().uploadEndpoint()).isEqualTo("POST /api/v1/uploads/documents");
        assertThat(docs.get(1).statusReason()).isEqualTo("NOT_SUBMITTED");
        assertThat(docs.get(1).fileKeys()).isEmpty();
        assertThat(service.documents(40L)).hasSize(2);
        assertThat(docs.get(0).name()).isEqualTo("National identity card");
        assertThat(docs.get(0).status()).isEqualTo("REJECTED");
        assertThat(docs.get(0).rejectionReason()).isEqualTo("Photo is blurry");
        assertThat(docs.get(0).reviewedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 0));
        assertThat(docs.get(0).expiresOn()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(docs.get(0).services()).extracting(x -> x.scope() + ":" + x.serviceName() + ":" + x.optionName())
                .containsExactly("GLOBAL:null:null", "OPTION:Caregiver:Hospital Care");
        assertThat(docs.get(1).status()).isEqualTo("MISSING");
        assertThat(docs.get(1).submissionId()).isNull();
    }

    @Test void noProfileYetReturnsContactsAndEmptySections() {
        when(customers.findByUserId(40L)).thenReturn(Optional.empty());
        var o = service.overview(40L, 5);
        assertThat(o.profile()).isNull();
        assertThat(o.contacts().email()).isEqualTo("madu@example.com");
        assertThat(o.documents()).isEmpty();
        assertThat(o.pendingServices().items()).isEmpty();
        assertThat(o.statistics().spending()).isEmpty();
    }

    @Test void recentLimitIsBounded() {
        assertThatThrownBy(() -> service.overview(40L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.overview(40L, 21)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- booking-request list ----------

    private BookingRequest listed(long id, String status, String payload) {
        BookingRequest r = new BookingRequest(); r.setRequestId(id); r.setUserId(40L); r.setOptionId(1L); r.setStatus(status);
        r.setPayloadJson(payload); r.setQuotedAmount(new BigDecimal("7000.00")); r.setCurrencyCode("LKR");
        r.setPaymentFundingMode("MOCK"); r.setPaymentStatus("NOT_STARTED");
        return r;
    }

    @Test void bookingRequestListMapsItemsAndPages() {
        var r = listed(9L, "CONFIRMED", "{\"booking\":{\"startDatetime\":\"2026-11-20T09:00:00\",\"expectedEndDatetime\":[2026,11,20,11,0]}}");
        r.setJobId(55L);
        var route = listed(8L, "MATCHING", "{\"booking\":{\"requestedPickupTime\":\"2026-11-21T07:30:00\"}}");
        var pageable = org.springframework.data.domain.PageRequest.of(0, 2);
        when(requests.findAllByUserId(org.mockito.ArgumentMatchers.eq(40L), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(r, route), pageable, 3));

        r.setMarketId(1L); route.setMarketId(1L);
        var colombo = new com.jobhub.entity.market.Market(); colombo.setMarketId(1L); colombo.setTimezone("Asia/Colombo");
        when(markets.findById(1L)).thenReturn(java.util.Optional.of(colombo));

        var page = service.bookingRequests(40L, null, 0, 2);
        org.mockito.Mockito.verify(markets, org.mockito.Mockito.times(1)).findById(1L);
        assertThat(page.items().get(0).marketId()).isEqualTo(1L);
        assertThat(page.items().get(0).marketTimezone()).isEqualTo("Asia/Colombo");
        assertThat(page.items().get(0).startAtUtc()).isEqualTo(java.time.Instant.parse("2026-11-20T03:30:00Z"));
        assertThat(page.items().get(0).endAtUtc()).isEqualTo(java.time.Instant.parse("2026-11-20T05:30:00Z"));
        assertThat(page.items().get(1).startAtUtc()).isEqualTo(java.time.Instant.parse("2026-11-21T02:00:00Z"));
        assertThat(page.items().get(1).endAtUtc()).isNull();
        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.hasMore()).isTrue();
        var first = page.items().get(0);
        assertThat(first.jobId()).isEqualTo(55L);
        assertThat(first.serviceName()).isEqualTo("Caregiver");
        assertThat(first.optionName()).isEqualTo("Hospital Care");
        assertThat(first.startAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
        assertThat(first.endAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 11, 0));
        assertThat(first.amount()).isEqualByComparingTo("7000");
        assertThat(page.items().get(1).startAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 7, 30));
        assertThat(page.items().get(1).jobId()).isNull();
    }

    @Test void bookingRequestStatusFilterIsNormalizedAndValidated() {
        when(requests.findAllByUserIdAndStatusIn(org.mockito.ArgumentMatchers.eq(40L), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service.bookingRequests(40L, List.of(" matching", "CONFIRMED", ""), 0, 20);
        @SuppressWarnings("unchecked")
        var statuses = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        org.mockito.Mockito.verify(requests).findAllByUserIdAndStatusIn(org.mockito.ArgumentMatchers.eq(40L), statuses.capture(), any());
        assertThat(statuses.getValue()).containsExactly("MATCHING", "CONFIRMED");

        assertThatThrownBy(() -> service.bookingRequests(40L, List.of("DONE"), 0, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown status: DONE");
        assertThatThrownBy(() -> service.bookingRequests(40L, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.bookingRequests(40L, null, 0, 51)).isInstanceOf(IllegalArgumentException.class);
    }
}
