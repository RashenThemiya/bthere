package com.jobhub.controller.customer;

import com.jobhub.dto.chat.ChatApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/bookings/{jobId}/messages/delivered")
    public DeliveryState delivered(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                                   @Valid @RequestBody MarkDelivered input) {
        return service.customerDelivered(user.id(), jobId, input);
    }

    @PostMapping(path = "/bookings/{jobId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Attachment upload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                             @RequestPart("file") MultipartFile file,
                             @RequestParam(required = false) String clientUploadId) {
        return service.customerUpload(user.id(), jobId, file, clientUploadId);
    }

    @GetMapping("/bookings/{jobId}/attachments/{attachmentId}")
    public Attachment attachment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                                 @PathVariable Long attachmentId) {
        return service.customerAttachment(user.id(), jobId, attachmentId);
    }

    @DeleteMapping("/bookings/{jobId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                                 @PathVariable Long attachmentId) {
        service.customerDeleteAttachment(user.id(), jobId, attachmentId);
    }
}
