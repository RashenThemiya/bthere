package com.jobhub.entity.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One text message in a booking's chat. The text is stored only as AES-GCM ciphertext bound to its job and sender;
 * phone numbers and emails are masked before encryption, so they are not stored in readable form either.
 */
@Entity
@Table(name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_message_job", columnList = "job_id,message_id"),
                @Index(name = "idx_chat_message_sender", columnList = "sender_user_id,created_at")},
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_message_client",
                columnNames = {"sender_user_id", "client_message_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;
    @Column(nullable = false)
    private Long jobId;
    @Column(nullable = false)
    private Long senderUserId;
    /** CUSTOMER or PROVIDER. */
    @Column(nullable = false, length = 20)
    private String senderRole;
    private Long senderProviderId;
    /** Optional client-generated id so a retried send does not create a duplicate. */
    @Column(length = 64)
    private String clientMessageId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyCiphertext;
    private boolean contactInfoHidden;
    @Column(nullable = false)
    private Instant createdAt;
}
