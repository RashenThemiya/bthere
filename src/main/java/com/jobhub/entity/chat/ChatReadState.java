package com.jobhub.entity.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** The newest message a participant has read in a booking chat. */
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
    @Column(nullable = false)
    private Instant updatedAt;
}
