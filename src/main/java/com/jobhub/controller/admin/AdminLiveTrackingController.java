package com.jobhub.controller.admin;

import com.jobhub.dto.job.LiveTrackingApi.*;
import com.jobhub.service.customer.LiveTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminLiveTrackingController {
    private final LiveTrackingService service;

    @GetMapping("/service-options/{optionId}/live-tracking")
    public Settings settings(@PathVariable Long optionId) { return service.settings(optionId); }

    @PutMapping("/service-options/{optionId}/live-tracking")
    public Settings update(@PathVariable Long optionId, @Valid @RequestBody Settings input) {
        return service.updateSettings(optionId, input);
    }

    @GetMapping("/bookings/{jobId}/tracking")
    public CustomerTracking tracking(@PathVariable Long jobId) { return service.adminTracking(jobId); }
}
