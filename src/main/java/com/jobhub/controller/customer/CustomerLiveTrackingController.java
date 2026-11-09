package com.jobhub.controller.customer;

import com.jobhub.dto.job.LiveTrackingApi.CustomerTracking;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.LiveTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerLiveTrackingController {
    private final LiveTrackingService service;

    @GetMapping("/bookings/{jobId}/tracking")
    public CustomerTracking tracking(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.customerTracking(user.id(), jobId);
    }
}
