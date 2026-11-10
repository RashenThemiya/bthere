package com.jobhub.controller.customer;

import com.jobhub.dto.job.CancellationApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.BookingCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/customers/me/bookings/{jobId}")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerCancellationController {
    private final BookingCancellationService service;

    @GetMapping("/cancellation-preview")
    public Quote preview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.customerPreview(user.id(), jobId);
    }

    @PostMapping("/cancel")
    public Cancellation cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                               @Valid @RequestBody Cancel input) {
        return service.cancelByCustomer(user.id(), jobId, input);
    }

    @GetMapping("/cancellation")
    public Cancellation cancellation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.customerCancellation(user.id(), jobId);
    }
}
