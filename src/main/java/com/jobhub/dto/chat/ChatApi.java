package com.jobhub.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChatApi {
    private ChatApi() {}

    /** clientMessageId is optional; resending the same id returns the original message instead of a duplicate. */
    public record SendMessage(
            @NotBlank(message = "Message text is required")
            @Size(max = 1000, message = "Message must not exceed 1000 characters")
            String text,
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "clientMessageId must be 8-64 letters, digits, - or _")
            String clientMessageId) {}

    public record MarkRead(@NotNull @Positive Long lastReadMessageId) {}

    /** role is CUSTOMER or PROVIDER; you is true for the signed-in user. */
    public record Participant(String role, Long providerId, String displayName, String profilePhoto, boolean you) {}

    /** text is null only if a stored message can no longer be decrypted. */
    public record Message(Long messageId, String senderRole, Long senderProviderId, String senderName, boolean fromYou,
                          String text, boolean contactInfoHidden, Instant sentAt, String clientMessageId) {}

    /**
     * canSend is false before confirmation and after the post-booking grace period; sendUntil is set once the
     * booking has ended. othersLastReadMessageId is the newest message any other participant has read ("seen").
     */
    public record ChatThread(Long jobId, String jobStatus, boolean canSend, Instant sendUntil,
                         List<Participant> participants, List<Message> messages, boolean hasOlder, boolean hasNewer,
                         Long yourLastReadMessageId, Long othersLastReadMessageId, long unreadCount) {}

    public record ReadState(Long jobId, Long yourLastReadMessageId, long unreadCount) {}

    public record LastMessage(Long messageId, String senderRole, boolean fromYou, String preview, Instant sentAt) {}

    public record Conversation(Long jobId, String jobStatus, boolean canSend, List<Participant> participants,
                               LastMessage lastMessage, long unreadCount) {}

    public record Conversations(List<Conversation> conversations, long totalUnread) {}
}
