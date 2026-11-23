package com.jobhub.entity.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A file uploaded into a booking chat. It is stored privately in S3 and only ever served through short-lived signed
 * URLs to the booking's participants. {@code messageId} is null until the file is sent with a message; unsent files
 * are deleted after a day.
 */
@Entity
@Table(name = "chat_attachment",
        indexes = {
                @Index(name = "idx_chat_attachment_message", columnList = "message_id"),
                @Index(name = "idx_chat_attachment_uploader", columnList = "uploader_user_id,created_at"),
                @Index(name = "idx_chat_attachment_unsent", columnList = "message_id,created_at")},
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_attachment_client",
                columnNames = {"uploader_user_id", "client_upload_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attachmentId;
    @Column(nullable = false)
    private Long jobId;
    @Column(nullable = false)
    private Long uploaderUserId;
    private Long messageId;
    /** Optional client-generated id so a retried upload does not store the file twice. */
    @Column(length = 64)
    private String clientUploadId;
    @Column(nullable = false, length = 300)
    private String storageKey;
    @Column(nullable = false, length = 150)
    private String fileName;
    @Column(nullable = false, length = 50)
    private String mimeType;
    @Column(nullable = false)
    private long sizeBytes;
    private Integer width;
    private Integer height;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant attachedAt;
}
