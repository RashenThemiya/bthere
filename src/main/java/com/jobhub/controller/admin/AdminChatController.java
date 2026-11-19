package com.jobhub.controller.admin;

import com.jobhub.dto.chat.ChatApi.ChatThread;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Read-only booking chat for disputes; every read is audited. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminChatController {
    private final ChatService service;

    @GetMapping("/bookings/{jobId}/messages")
    public ChatThread messages(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                           @RequestParam(required = false) Long after) {
        return service.adminThread(user.id(), jobId, after);
    }
}
