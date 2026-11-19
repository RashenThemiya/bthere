package com.jobhub.repository.chat;

import com.jobhub.entity.chat.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50ByJobIdOrderByMessageIdDesc(Long jobId);

    List<ChatMessage> findTop50ByJobIdAndMessageIdGreaterThanOrderByMessageIdAsc(Long jobId, Long after);

    List<ChatMessage> findTop50ByJobIdAndMessageIdLessThanOrderByMessageIdDesc(Long jobId, Long before);

    Optional<ChatMessage> findTopByJobIdOrderByMessageIdDesc(Long jobId);

    boolean existsByJobId(Long jobId);

    boolean existsByJobIdAndMessageIdGreaterThan(Long jobId, Long messageId);

    boolean existsByJobIdAndMessageIdLessThan(Long jobId, Long messageId);

    long countByJobIdAndMessageIdGreaterThanAndSenderUserIdNot(Long jobId, Long after, Long userId);

    long countBySenderUserIdAndCreatedAtAfter(Long senderUserId, Instant since);

    Optional<ChatMessage> findTopBySenderUserIdOrderByMessageIdDesc(Long senderUserId);

    Optional<ChatMessage> findBySenderUserIdAndClientMessageId(Long senderUserId, String clientMessageId);
}
