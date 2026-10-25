package com.jobhub.controller.customer;

import com.jobhub.dto.job.BookingRequestsApi.*;
import com.jobhub.entity.job.BookingDispatchEvent;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.BookingRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerBookingRequestController {
    private final BookingRequestService service;
    @GetMapping("/booking-requests/policy")
    public Policy policy(@RequestParam Long optionId) {return service.policy(optionId);}
    @PostMapping("/booking-requests") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public View create(@AuthenticationPrincipal AuthenticatedUser user,@RequestHeader("Idempotency-Key") String key,
                       @Valid @RequestBody Create input) {return service.create(user.id(),key,input);}
    @GetMapping("/booking-requests/{id}")
    public View get(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {return service.get(user.id(),id);}
    @PostMapping("/booking-requests/{id}/confirm")
    public Checkout confirm(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@Valid @RequestBody Confirm input) {
        return service.confirm(user.id(),id,input);
    }
    @PostMapping("/booking-requests/{id}/cancel")
    public View cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {return service.cancel(user.id(),id);}
    @GetMapping("/bookings/{jobId}/verification-codes")
    public Codes codes(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long jobId) {return service.customerCodes(user.id(),jobId);}
    @PostMapping("/bookings/{jobId}/confirm-completion")
    public Transition confirmCompletion(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long jobId) {return service.confirmCompletion(user.id(),jobId);}
    @GetMapping("/booking-events")
    public List<BookingDispatchEvent> events(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="0") Long after) {
        if(after<0)throw new IllegalArgumentException("after must be nonnegative");return service.events(user.id(),after);
    }
}
