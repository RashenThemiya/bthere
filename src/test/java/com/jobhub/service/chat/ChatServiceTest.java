package com.jobhub.service.chat;

import com.jobhub.dto.chat.ChatApi.MarkRead;
import com.jobhub.dto.chat.ChatApi.SendMessage;
import com.jobhub.entity.chat.ChatMessage;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.BookingDispatchEvent;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobProviderAssignment;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.chat.ChatMessageRepository;
import com.jobhub.repository.chat.ChatReadStateRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.BookingDispatchEventRepository;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.upload.ProfilePhotoUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {
    private static final long JOB = 40L, CUSTOMER_USER = 21L, CUSTOMER_ID = 5L;
    private static final long PROVIDER_USER = 30L, PROVIDER_ID = 14L, TEAM_USER = 31L, TEAM_ID = 16L;

    @Mock JobRepository jobs;
    @Mock CustomerRepository customers;
    @Mock ServiceProviderRepository providers;
    @Mock JobProviderAssignmentRepository jobAssignments;
    @Mock ChatMessageRepository messages;
    @Mock ChatReadStateRepository reads;
    @Mock BookingRequestRepository requests;
    @Mock BookingDispatchEventRepository events;
    @Mock AuditService audit;
    @Spy ChatCipher cipher = new ChatCipher("", Base64.getEncoder().encodeToString(new byte[32]));
    @Spy ProfilePhotoUrls photoUrls = new ProfilePhotoUrls("jobhube", "eu-north-1", "", "https://api.test");
    @InjectMocks ChatService service;

    Job job;

    @BeforeEach
    void setUp() {
        job = new Job();
        job.setJobId(JOB);
        job.setCustomerId(CUSTOMER_ID);
        job.setAssignedServiceProviderId(PROVIDER_ID);
        job.setJobStatus("CONFIRMED");
        job.setUpdatedAt(LocalDateTime.now());
        when(jobs.findById(JOB)).thenReturn(Optional.of(job));

        var customer = new Customer();
        customer.setCustomerId(CUSTOMER_ID); customer.setUserId(CUSTOMER_USER);
        customer.setFirstName("Madu"); customer.setLastName("Perera");
        when(customers.findByUserId(CUSTOMER_USER)).thenReturn(Optional.of(customer));
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        provider(PROVIDER_ID, PROVIDER_USER, "Tharushi");
        provider(TEAM_ID, TEAM_USER, "Nethmi");
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(JOB)).thenReturn(List.of());

        var request = new BookingRequest(); request.setRequestId(900L);
        when(requests.findByJobId(JOB)).thenReturn(Optional.of(request));
        when(messages.save(any())).thenAnswer(i -> { ChatMessage m = i.getArgument(0); m.setMessageId(101L); return m; });
        when(messages.findTop50ByJobIdOrderByMessageIdDesc(JOB)).thenReturn(List.of());
        when(reads.findByJobIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(reads.findAllByJobId(anyLong())).thenReturn(List.of());
    }

    @Test
    void customerMessageIsEncryptedMaskedAndPushedToTheProviderOnly() {
        var sent = service.customerSend(CUSTOMER_USER, JOB, new SendMessage("Call 0771234567 at the gate", null));

        var saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getBodyCiphertext()).startsWith("v1:").doesNotContain("gate").doesNotContain("0771234567");
        assertThat(saved.getValue().getSenderRole()).isEqualTo("CUSTOMER");
        assertThat(sent.text()).isEqualTo("Call [hidden] at the gate");
        assertThat(sent.contactInfoHidden()).isTrue();
        assertThat(sent.fromYou()).isTrue();
        assertThat(sent.senderName()).isEqualTo("Madu Perera");

        var pushed = ArgumentCaptor.forClass(BookingDispatchEvent.class);
        verify(events).save(pushed.capture());
        assertThat(pushed.getValue().getUserId()).isEqualTo(PROVIDER_USER);
        assertThat(pushed.getValue().getJobId()).isEqualTo(JOB);
        assertThat(pushed.getValue().getRequestId()).isEqualTo(900L);
        assertThat(pushed.getValue().getEventType()).isEqualTo("CHAT_MESSAGE_FROM_CUSTOMER");
    }

    @Test
    void providerMessageReachesCustomerAndTeamButPushesAreCoalesced() {
        teamMember("ACCEPTED");
        when(events.existsByUserIdAndJobIdAndEventTypeAndCreatedAtAfter(eq(TEAM_USER), eq(JOB), any(), any())).thenReturn(true);

        service.providerSend(PROVIDER_USER, JOB, new SendMessage("On my way", null));

        var pushed = ArgumentCaptor.forClass(BookingDispatchEvent.class);
        verify(events).save(pushed.capture());
        assertThat(pushed.getAllValues()).extracting(BookingDispatchEvent::getUserId).containsExactly(CUSTOMER_USER);
        assertThat(pushed.getValue().getEventType()).isEqualTo("CHAT_MESSAGE_FROM_PROVIDER");
    }

    @Test
    void outsidersGetBookingNotFound() {
        var other = new Customer(); other.setCustomerId(99L); other.setUserId(77L);
        when(customers.findByUserId(77L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.customerThread(77L, JOB, null, null)).isInstanceOf(ResourceNotFoundException.class);

        provider(50L, 55L, "Stranger");
        assertThatThrownBy(() -> service.providerSend(55L, JOB, new SendMessage("hi", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        teamMember("DECLINED");
        assertThatThrownBy(() -> service.providerThread(TEAM_USER, JOB, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void activeTeamMemberCanChat() {
        teamMember("ACCEPTED");
        var thread = service.providerThread(TEAM_USER, JOB, null, null);
        assertThat(thread.canSend()).isTrue();
        assertThat(thread.participants()).extracting(p -> p.displayName() + ":" + p.you())
                .containsExactly("Madu Perera:false", "Tharushi Silva:false", "Nethmi Silva:true");
    }

    @Test
    void chatIsClosedBeforeConfirmation() {
        job.setJobStatus("AWAITING_PAYMENT");
        assertThatThrownBy(() -> service.customerThread(CUSTOMER_USER, JOB, null, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hi", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completedBookingAcceptsMessagesFor24HoursThenIsReadOnly() {
        job.setJobStatus("COMPLETED");
        job.setUpdatedAt(LocalDateTime.now().minusHours(23));
        assertThat(service.customerThread(CUSTOMER_USER, JOB, null, null).canSend()).isTrue();

        job.setUpdatedAt(LocalDateTime.now().minusHours(25));
        when(messages.existsByJobId(JOB)).thenReturn(true);
        var thread = service.customerThread(CUSTOMER_USER, JOB, null, null);
        assertThat(thread.canSend()).isFalse();
        assertThat(thread.sendUntil()).isBefore(Instant.now());
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hi", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void bookingCancelledBeforeAnyChatDoesNotOpenOne() {
        job.setJobStatus("CANCELLED");
        when(messages.existsByJobId(JOB)).thenReturn(false);
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hi", null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.customerThread(CUSTOMER_USER, JOB, null, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void sendingIsRateLimited() {
        var recent = new ChatMessage(); recent.setCreatedAt(Instant.now());
        when(messages.findTopBySenderUserIdOrderByMessageIdDesc(CUSTOMER_USER)).thenReturn(Optional.of(recent));
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hi", null)))
                .isInstanceOf(TooManyRequestsException.class);

        recent.setCreatedAt(Instant.now().minusSeconds(5));
        when(messages.countBySenderUserIdAndCreatedAtAfter(eq(CUSTOMER_USER), any())).thenReturn(30L);
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hi", null)))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(60));
        verify(messages, never()).save(any());
    }

    @Test
    void retriedClientMessageIdReturnsTheOriginalMessage() {
        var original = stored(55L, CUSTOMER_USER, "CUSTOMER", "hello");
        original.setClientMessageId("retry-1234");
        when(messages.findBySenderUserIdAndClientMessageId(CUSTOMER_USER, "retry-1234")).thenReturn(Optional.of(original));

        var result = service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hello", "retry-1234"));

        assertThat(result.messageId()).isEqualTo(55L);
        assertThat(result.text()).isEqualTo("hello");
        verify(messages, never()).save(any());
        verify(events, never()).save(any());
    }

    @Test
    void threadDecryptsAndMarksOwnMessages() {
        var page = List.of(stored(2L, PROVIDER_USER, "PROVIDER", "On my way"), stored(1L, CUSTOMER_USER, "CUSTOMER", "Hi"));
        when(messages.findTop50ByJobIdOrderByMessageIdDesc(JOB)).thenReturn(page);
        when(messages.countByJobIdAndMessageIdGreaterThanAndSenderUserIdNot(JOB, 0L, CUSTOMER_USER)).thenReturn(1L);

        var thread = service.customerThread(CUSTOMER_USER, JOB, null, null);

        assertThat(thread.messages()).extracting(m -> m.messageId() + ":" + m.text() + ":" + m.fromYou())
                .containsExactly("1:Hi:true", "2:On my way:false");
        assertThat(thread.unreadCount()).isEqualTo(1);
    }

    @Test
    void cannotMarkAMessageFromAnotherChatAsRead() {
        var foreign = stored(9L, PROVIDER_USER, "PROVIDER", "x"); foreign.setJobId(41L);
        when(messages.findById(9L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.customerRead(CUSTOMER_USER, JOB, new MarkRead(9L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminReadsAreAudited() {
        var page = List.of(stored(1L, CUSTOMER_USER, "CUSTOMER", "Hi"));
        when(messages.findTop50ByJobIdAndMessageIdGreaterThanOrderByMessageIdAsc(JOB, 0L)).thenReturn(page);
        var thread = service.adminThread(1L, JOB, null);
        assertThat(thread.canSend()).isFalse();
        assertThat(thread.messages()).extracting(m -> m.text()).containsExactly("Hi");
        verify(audit).record(1L, "ADMIN_CHAT_VIEWED", "JOB", JOB, null, Map.of("after", 0L));
    }

    private ChatMessage stored(Long id, Long sender, String role, String text) {
        var m = new ChatMessage();
        m.setMessageId(id); m.setJobId(JOB); m.setSenderUserId(sender); m.setSenderRole(role);
        m.setBodyCiphertext(cipher.encrypt(JOB, sender, text)); m.setCreatedAt(Instant.now().minusSeconds(60));
        return m;
    }

    private void provider(Long id, Long userId, String first) {
        var p = new ServiceProvider();
        p.setServiceProviderId(id); p.setUserId(userId); p.setFirstName(first); p.setLastName("Silva");
        when(providers.findByUserId(userId)).thenReturn(Optional.of(p));
        when(providers.findById(id)).thenReturn(Optional.of(p));
    }

    private void teamMember(String status) {
        var a = new JobProviderAssignment(); a.setJobId(JOB); a.setServiceProviderId(TEAM_ID); a.setStatus(status);
        when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(JOB)).thenReturn(List.of(a));
    }
}
