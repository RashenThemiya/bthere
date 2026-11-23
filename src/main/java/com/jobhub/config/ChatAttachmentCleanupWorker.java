package com.jobhub.config;

import com.jobhub.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Removes chat files that were uploaded but not sent within 24 hours. */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatAttachmentCleanupWorker {
    private final ChatService chat;

    @Scheduled(initialDelayString = "${chat.attachment-cleanup-ms:3600000}", fixedDelayString = "${chat.attachment-cleanup-ms:3600000}")
    public void purge() {
        try {
            int removed = chat.purgeUnsentAttachments();
            if (removed > 0) log.info("Removed {} unsent chat attachments", removed);
        } catch (RuntimeException e) {
            log.warn("Chat attachment cleanup failed: {}", e.getMessage());
        }
    }
}
