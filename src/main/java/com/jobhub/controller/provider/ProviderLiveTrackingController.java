package com.jobhub.controller.provider;

import com.jobhub.dto.job.LiveTrackingApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.LiveTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/providers/me")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderLiveTrackingController {
    private final LiveTrackingService service;

    @GetMapping("/bookings/{jobId}/tracking")
    public ProviderState state(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.providerState(user.id(), jobId);
    }

    @PostMapping("/bookings/{jobId}/start-journey")
    public ProviderState startJourney(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.startJourney(user.id(), jobId);
    }

    @PostMapping("/bookings/{jobId}/arrived")
    public ProviderState arrived(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.arrived(user.id(), jobId);
    }

    @PostMapping("/location")
    public LocationAccepted location(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody LocationUpdate input) {
        return service.updateLocation(user.id(), input);
    }
}
