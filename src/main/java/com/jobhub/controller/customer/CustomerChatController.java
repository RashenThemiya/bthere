package com.jobhub.controller.customer;

import com.jobhub.dto.chat.ChatApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Booking chat for the signed-in customer. See docs/CHAT_API.md. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerChatController {
    private final ChatService service;

    @GetMapping("/conversations")
    public Conversations conversations(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.customerConversations(user.id());
    }

    @GetMapping("/bookings/{jobId}/messages")
    public ChatThread messages(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                           @RequestParam(required = false) Long after, @RequestParam(required = false) Long before) {
        return service.customerThread(user.id(), jobId, after, before);
    }

    @PostMapping("/bookings/{jobId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                        @Valid @RequestBody SendMessage input) {
        return service.customerSend(user.id(), jobId, input);
    }

    @PostMapping("/bookings/{jobId}/messages/read")
    public ReadState read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                          @Valid @RequestBody MarkRead input) {
        return service.customerRead(user.id(), jobId, input);
    }
}
