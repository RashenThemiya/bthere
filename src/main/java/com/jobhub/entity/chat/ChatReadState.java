package com.jobhub.entity.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A participant's position in a booking chat: the newest message their app has received (delivered) and the newest
 * they have read. Both only move forward; reading also counts as receiving.
 */
@Entity
@Table(name = "chat_read_state",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_read_state", columnNames = {"job_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatReadState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long readStateId;
    @Column(nullable = false)
    private Long jobId;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long lastReadMessageId = 0L;
    /** Null for rows created before delivery tracking; treat as 0. */
    private Long lastDeliveredMessageId;
    @Column(nullable = false)
    private Instant updatedAt;
}
