package com.jobhub.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChatApi {
    private ChatApi() {}

    /**
     * text may be empty when attachmentIds is not; at least one is required. clientMessageId is optional; resending
     * the same id returns the original message instead of a duplicate.
     */
    public record SendMessage(
            @Size(max = 1000, message = "Message must not exceed 1000 characters")
            String text,
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "clientMessageId must be 8-64 letters, digits, - or _")
            String clientMessageId,
            @Size(max = 5, message = "A message can have at most 5 attachments")
            List<@NotNull @Positive Long> attachmentIds) {
        public SendMessage(String text, String clientMessageId) {
            this(text, clientMessageId, null);
        }
    }

    public record MarkRead(@NotNull @Positive Long lastReadMessageId) {}

    public record MarkDelivered(@NotNull @Positive Long lastDeliveredMessageId) {}

    /** role is CUSTOMER or PROVIDER; you is true for the signed-in user. */
    public record Participant(String role, Long providerId, String displayName, String profilePhoto, boolean you) {}

    /**
     * A chat file. url is a signed link that works without auth headers until urlExpiresAt; fetch the attachment or
     * the thread again for a new one. messageId is null while the file is uploaded but not sent yet.
     */
    public record Attachment(Long attachmentId, String kind, String fileName, String mimeType, long sizeBytes,
                             Integer width, Integer height, String url, Instant urlExpiresAt, Long messageId,
                             Instant uploadedAt, Instant deleteIfUnsentAt) {}

    /**
     * text is "" for an attachment-only message and null only if a stored message can no longer be decrypted.
     * deliveryStatus is set on your own messages only: SENT, DELIVERED, SEEN or UNKNOWN (older messages sent before
     * delivery receipts existed, not yet seen).
     */
    public record Message(Long messageId, String senderRole, Long senderProviderId, String senderName, boolean fromYou,
                          String text, boolean contactInfoHidden, Instant sentAt, String clientMessageId,
                          List<Attachment> attachments, String deliveryStatus) {}

    /** One other participant's position: the newest message their app received and the newest they read. */
    public record Receipt(String role, Long providerId, String displayName, long lastDeliveredMessageId,
                          long lastReadMessageId) {}

    /**
     * canSend is false before confirmation and after the post-booking grace period; sendUntil is set once the
     * booking has ended. othersLastReadMessageId is the newest message any other participant has read.
     * othersLastDeliveredMessageId is the newest message every other participant has received.
     */
    public record ChatThread(Long jobId, String jobStatus, boolean canSend, Instant sendUntil,
                         List<Participant> participants, List<Message> messages, boolean hasOlder, boolean hasNewer,
                         Long yourLastReadMessageId, Long othersLastReadMessageId, long unreadCount,
                         Long othersLastDeliveredMessageId, List<Receipt> receipts) {}

    public record ReadState(Long jobId, Long yourLastReadMessageId, long unreadCount) {}

    public record DeliveryState(Long jobId, Long yourLastDeliveredMessageId, Long yourLastReadMessageId) {}

    /** preview is null for an attachment-only message; use attachmentCount to show "Photo" or "File". */
    public record LastMessage(Long messageId, String senderRole, boolean fromYou, String preview, Instant sentAt,
                              int attachmentCount) {}

    public record Conversation(Long jobId, String jobStatus, boolean canSend, List<Participant> participants,
                               LastMessage lastMessage, long unreadCount) {}

    public record Conversations(List<Conversation> conversations, long totalUnread) {}
}
