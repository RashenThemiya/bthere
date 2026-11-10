package com.jobhub.controller.provider;

import com.jobhub.dto.job.CancellationApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.BookingCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** A provider cancelling a paid, not-yet-started booking; the customer is refunded in full to their wallet. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/providers/me/bookings/{jobId}")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderCancellationController {
    private final BookingCancellationService service;

    /** Penalty the provider would pay for cancelling now; nothing is saved. */
    @GetMapping("/cancellation-preview")
    public Quote preview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.providerPreview(user.id(), jobId);
    }

    @PostMapping("/cancel")
    public Cancellation cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                               @Valid @RequestBody Cancel input) {
        return service.cancelByProvider(user.id(), jobId, input);
    }

    @GetMapping("/cancellation")
    public Cancellation cancellation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.providerCancellation(user.id(), jobId);
    }
}
