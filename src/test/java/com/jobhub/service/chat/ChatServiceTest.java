package com.jobhub.service.chat;

import com.jobhub.dto.chat.ChatApi.MarkDelivered;
import com.jobhub.dto.chat.ChatApi.MarkRead;
import com.jobhub.dto.chat.ChatApi.Message;
import com.jobhub.dto.chat.ChatApi.Receipt;
import com.jobhub.dto.chat.ChatApi.SendMessage;
import com.jobhub.entity.chat.ChatAttachment;
import com.jobhub.entity.chat.ChatMessage;
import com.jobhub.entity.chat.ChatReadState;
import com.jobhub.exception.ApiException;
import com.jobhub.exception.InvalidInputException;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
    @Mock com.jobhub.repository.chat.ChatAttachmentRepository attachments;
    @Mock ChatAttachmentStorage storage;
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

    // ---------- delivery receipts ----------

    private ChatMessage own(long id, Boolean tracked) {
        var m = new ChatMessage(); m.setMessageId(id); m.setJobId(JOB); m.setSenderUserId(CUSTOMER_USER);
        m.setSenderRole("CUSTOMER"); m.setCreatedAt(Instant.now()); m.setDeliveryTracked(tracked);
        m.setBodyCiphertext(cipher.encrypt(JOB, CUSTOMER_USER, "msg " + id));
        return m;
    }

    private ChatReadState state(long userId, Long delivered, long read) {
        var s = new ChatReadState(); s.setReadStateId(userId); s.setJobId(JOB); s.setUserId(userId);
        s.setLastDeliveredMessageId(delivered); s.setLastReadMessageId(read); s.setUpdatedAt(Instant.now());
        return s;
    }

    @Test
    void ownMessagesShowSeenDeliveredSentOrUnknownForOldUntrackedMessages() {
        var page = List.of(own(70, true), own(60, null), own(50, null));
        when(messages.findTop50ByJobIdOrderByMessageIdDesc(JOB)).thenReturn(page);
        when(reads.findAllByJobId(JOB)).thenReturn(List.of(state(PROVIDER_USER, 55L, 50)));

        var thread = service.customerThread(CUSTOMER_USER, JOB, null, null);

        assertThat(thread.messages()).extracting(m -> m.messageId() + ":" + m.deliveryStatus())
                .containsExactly("50:SEEN", "60:UNKNOWN", "70:SENT");
        assertThat(thread.othersLastDeliveredMessageId()).isEqualTo(55L);
        assertThat(thread.receipts()).singleElement().satisfies(r -> {
            assertThat(r.role()).isEqualTo("PROVIDER");
            assertThat(r.lastDeliveredMessageId()).isEqualTo(55L);
            assertThat(r.lastReadMessageId()).isEqualTo(50L);
        });

        when(reads.findAllByJobId(JOB)).thenReturn(List.of(state(PROVIDER_USER, 70L, 50)));
        assertThat(service.customerThread(CUSTOMER_USER, JOB, null, null).messages())
                .extracting(Message::deliveryStatus).containsExactly("SEEN", "DELIVERED", "DELIVERED");
    }

    @Test
    void othersMessagesHaveNoDeliveryStatus() {
        var fromProvider = own(80, true); fromProvider.setSenderUserId(PROVIDER_USER); fromProvider.setSenderRole("PROVIDER");
        fromProvider.setBodyCiphertext(cipher.encrypt(JOB, PROVIDER_USER, "hi"));
        when(messages.findTop50ByJobIdOrderByMessageIdDesc(JOB)).thenReturn(List.of(fromProvider));
        assertThat(service.customerThread(CUSTOMER_USER, JOB, null, null).messages().get(0).deliveryStatus()).isNull();
    }

    @Test
    void teamChatCountsAsDeliveredAndSeenOnlyWhenEveryOtherParticipantHasIt() {
        teamMember("ACCEPTED");
        var page = List.of(own(70, true));
        when(messages.findTop50ByJobIdOrderByMessageIdDesc(JOB)).thenReturn(page);
        when(reads.findAllByJobId(JOB)).thenReturn(List.of(state(PROVIDER_USER, 70L, 70)));

        var thread = service.customerThread(CUSTOMER_USER, JOB, null, null);
        assertThat(thread.messages().get(0).deliveryStatus()).isEqualTo("SENT");
        assertThat(thread.othersLastReadMessageId()).isEqualTo(70L);
        assertThat(thread.othersLastDeliveredMessageId()).isZero();
        assertThat(thread.receipts()).extracting(Receipt::providerId).containsExactly(PROVIDER_ID, TEAM_ID);

        when(reads.findAllByJobId(JOB)).thenReturn(List.of(state(PROVIDER_USER, 70L, 70), state(TEAM_USER, 70L, 0)));
        assertThat(service.customerThread(CUSTOMER_USER, JOB, null, null).messages().get(0).deliveryStatus()).isEqualTo("DELIVERED");
        when(reads.findAllByJobId(JOB)).thenReturn(List.of(state(PROVIDER_USER, 70L, 70), state(TEAM_USER, null, 70)));
        assertThat(service.customerThread(CUSTOMER_USER, JOB, null, null).messages().get(0).deliveryStatus()).isEqualTo("SEEN");
    }

    @Test
    void deliveredAcksOnlyMoveForwardAndRepeatsAreHarmless() {
        var existing = state(PROVIDER_USER, 105L, 90);
        when(reads.findByJobIdAndUserId(JOB, PROVIDER_USER)).thenReturn(Optional.of(existing));
        var m103 = own(103, true);
        var m110 = own(110, true);
        when(messages.findById(103L)).thenReturn(Optional.of(m103));
        when(messages.findById(110L)).thenReturn(Optional.of(m110));

        var stale = service.providerDelivered(PROVIDER_USER, JOB, new MarkDelivered(103L));
        assertThat(stale.yourLastDeliveredMessageId()).isEqualTo(105L);
        verify(reads, never()).save(any());

        var moved = service.providerDelivered(PROVIDER_USER, JOB, new MarkDelivered(110L));
        assertThat(moved.yourLastDeliveredMessageId()).isEqualTo(110L);
        assertThat(moved.yourLastReadMessageId()).isEqualTo(90L);
        service.providerDelivered(PROVIDER_USER, JOB, new MarkDelivered(110L));
        verify(reads, times(1)).save(existing);

        var other = own(5, true); other.setJobId(99L);
        when(messages.findById(5L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.providerDelivered(PROVIDER_USER, JOB, new MarkDelivered(5L)))
                .isInstanceOf(InvalidInputException.class).extracting("code").isEqualTo("MESSAGE_NOT_IN_CHAT");
    }

    @Test
    void readingAlsoCountsAsDelivered() {
        var m120 = own(120, true);
        when(messages.findById(120L)).thenReturn(Optional.of(m120));
        service.providerRead(PROVIDER_USER, JOB, new MarkRead(120L));
        var saved = ArgumentCaptor.forClass(ChatReadState.class);
        verify(reads).save(saved.capture());
        assertThat(saved.getValue().getLastReadMessageId()).isEqualTo(120L);
        assertThat(saved.getValue().getLastDeliveredMessageId()).isEqualTo(120L);
    }

    @Test
    void newMessagesAreDeliveryTracked() {
        service.customerSend(CUSTOMER_USER, JOB, new SendMessage("hello", null));
        var saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getDeliveryTracked()).isTrue();
    }

    // ---------- attachments ----------

    private ChatAttachment file(long id, long uploader, Long messageId) {
        var a = new ChatAttachment(); a.setAttachmentId(id); a.setJobId(JOB); a.setUploaderUserId(uploader);
        a.setMessageId(messageId); a.setStorageKey("chat/40/" + id + ".png"); a.setFileName("gate " + id + ".png");
        a.setMimeType(id % 2 == 0 ? "image/png" : "application/pdf"); a.setSizeBytes(2048); a.setCreatedAt(Instant.now());
        return a;
    }

    private static byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private void signing() {
        when(storage.signedUrl(any(), any(), any(), anyBoolean()))
                .thenReturn(new ChatAttachmentStorage.SignedUrl("https://s3.test/signed", Instant.parse("2030-01-01T00:15:00Z")));
    }

    @Test
    void attachmentOnlyMessageLinksFilesInTheGivenOrder() {
        signing();
        var photo = file(8, CUSTOMER_USER, null);
        var pdf = file(7, CUSTOMER_USER, null);
        when(attachments.lockAllById(List.of(8L, 7L))).thenReturn(List.of(pdf, photo));
        when(attachments.findAllByMessageIdInOrderByAttachmentIdAsc(List.of(101L))).thenReturn(List.of(photo, pdf));

        var sent = service.customerSend(CUSTOMER_USER, JOB, new SendMessage(null, "c-files-0001", List.of(8L, 7L)));

        assertThat(photo.getMessageId()).isEqualTo(101L);
        assertThat(pdf.getMessageId()).isEqualTo(101L);
        assertThat(sent.text()).isEmpty();
        assertThat(sent.attachments()).extracting(a -> a.attachmentId() + ":" + a.kind())
                .containsExactly("8:IMAGE", "7:DOCUMENT");
        assertThat(sent.attachments().get(0).url()).isEqualTo("https://s3.test/signed");
        assertThat(sent.attachments().get(0).deleteIfUnsentAt()).isNull();
        verify(storage).signedUrl("chat/40/8.png", "image/png", "gate 8.png", true);
        verify(storage).signedUrl("chat/40/7.png", "application/pdf", "gate 7.png", false);
    }

    @Test
    void sendRejectsForeignAlreadySentTooManyOrNoContent() {
        when(attachments.lockAllById(List.of(9L))).thenReturn(List.of(file(9, TEAM_USER, null)));
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("", null, List.of(9L))))
                .extracting("code").isEqualTo("ATTACHMENT_NOT_FOUND");
        when(attachments.lockAllById(List.of(10L))).thenReturn(List.of(file(10, CUSTOMER_USER, 55L)));
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("", null, List.of(10L))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("ATTACHMENT_ALREADY_SENT");
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB, new SendMessage("  ", null, List.of())))
                .extracting("code").isEqualTo("MESSAGE_EMPTY");
        assertThatThrownBy(() -> service.customerSend(CUSTOMER_USER, JOB,
                new SendMessage("x", null, List.of(1L, 2L, 3L, 4L, 5L, 6L))))
                .extracting("code").isEqualTo("TOO_MANY_ATTACHMENTS");
        verify(messages, never()).save(any());
    }

    @Test
    void uploadTrustsFileBytesNotTheClientTypeAndStoresPrivately() throws Exception {
        signing();
        when(attachments.saveAndFlush(any())).thenAnswer(i -> {
            ChatAttachment a = i.getArgument(0); a.setAttachmentId(77L); return a;
        });
        var upload = new MockMultipartFile("file", "../call 0771234567.exe", "application/pdf", png(40, 30));

        var a = service.customerUpload(CUSTOMER_USER, JOB, upload, "u-12345678");

        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), eq("image/png"));
        assertThat(key.getValue()).matches("chat/40/[0-9a-f-]{36}\\.png");
        assertThat(a.attachmentId()).isEqualTo(77L);
        assertThat(a.fileName()).isEqualTo("call [hidden].png");
        assertThat(a.mimeType()).isEqualTo("image/png");
        assertThat(a.width()).isEqualTo(40);
        assertThat(a.height()).isEqualTo(30);
        assertThat(a.messageId()).isNull();
        assertThat(a.deleteIfUnsentAt()).isAfter(Instant.now().plusSeconds(23 * 3600));

        var gif = new MockMultipartFile("file", "a.png", "image/png", "GIF89a....".getBytes());
        assertThatThrownBy(() -> service.customerUpload(CUSTOMER_USER, JOB, gif, null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("UNSUPPORTED_FILE_TYPE");
        verify(storage, times(1)).put(any(), any(), any());
    }

    @Test
    void retriedUploadReturnsTheSameFileAndUnconfirmedChatsRejectUploads() throws Exception {
        signing();
        when(attachments.findByUploaderUserIdAndClientUploadId(CUSTOMER_USER, "u-12345678"))
                .thenReturn(Optional.of(file(12, CUSTOMER_USER, null)));
        var upload = new MockMultipartFile("file", "x.png", "image/png", png(2, 2));
        assertThat(service.customerUpload(CUSTOMER_USER, JOB, upload, "u-12345678").attachmentId()).isEqualTo(12L);
        verify(storage, never()).put(any(), any(), any());

        job.setJobStatus("AWAITING_PAYMENT");
        assertThatThrownBy(() -> service.customerUpload(CUSTOMER_USER, JOB, upload, null))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHAT_NOT_OPEN");
    }

    @Test
    void unsentFilesAreVisibleOnlyToTheUploaderAndOnlyUnsentFilesCanBeRemoved() {
        signing();
        var unsent = file(20, CUSTOMER_USER, null);
        var sent = file(22, CUSTOMER_USER, 101L);
        when(attachments.findById(20L)).thenReturn(Optional.of(unsent));
        when(attachments.findById(22L)).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> service.providerAttachment(PROVIDER_USER, JOB, 20L))
                .isInstanceOf(ResourceNotFoundException.class).extracting("code").isEqualTo("ATTACHMENT_NOT_FOUND");
        assertThat(service.providerAttachment(PROVIDER_USER, JOB, 22L).url()).isEqualTo("https://s3.test/signed");
        assertThatThrownBy(() -> service.customerDeleteAttachment(CUSTOMER_USER, JOB, 22L))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("ATTACHMENT_ALREADY_SENT");

        service.customerDeleteAttachment(CUSTOMER_USER, JOB, 20L);
        verify(storage).delete("chat/40/20.png");
        verify(attachments).delete(unsent);
    }

    @Test
    void cleanupRemovesStaleUnsentFilesEvenWhenAnObjectDeleteFails() {
        var ok = file(30, CUSTOMER_USER, null);
        var stuck = file(31, CUSTOMER_USER, null);
        when(attachments.findTop100ByMessageIdIsNullAndCreatedAtBeforeOrderByAttachmentIdAsc(any())).thenReturn(List.of(ok, stuck));
        doThrow(new RuntimeException("Access Denied")).when(storage).delete("chat/40/31.png");

        assertThat(service.purgeUnsentAttachments()).isEqualTo(2);

        verify(storage).delete("chat/40/30.png");
        verify(attachments).delete(ok);
        verify(attachments).delete(stuck);
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
