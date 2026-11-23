package com.jobhub.repository.chat;

import com.jobhub.entity.chat.ChatAttachment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    List<ChatAttachment> findAllByMessageIdInOrderByAttachmentIdAsc(Collection<Long> messageIds);

    long countByMessageId(Long messageId);

    Optional<ChatAttachment> findByUploaderUserIdAndClientUploadId(Long uploaderUserId, String clientUploadId);

    long countByUploaderUserIdAndCreatedAtAfter(Long uploaderUserId, Instant since);

    long countByUploaderUserIdAndJobIdAndMessageIdIsNull(Long uploaderUserId, Long jobId);

    List<ChatAttachment> findTop100ByMessageIdIsNullAndCreatedAtBeforeOrderByAttachmentIdAsc(Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChatAttachment a where a.attachmentId in :ids")
    List<ChatAttachment> lockAllById(@Param("ids") Collection<Long> ids);
}
