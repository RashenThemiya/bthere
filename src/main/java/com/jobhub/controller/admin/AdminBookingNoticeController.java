package com.jobhub.controller.admin;

import com.jobhub.dto.job.BookingNoticeApi.Settings;
import com.jobhub.service.customer.BookingNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBookingNoticeController {
    private final BookingNoticeService service;

    @GetMapping("/service-options/{optionId}/booking-notice")
    public Settings settings(@PathVariable Long optionId) { return service.settings(optionId); }

    @PutMapping("/service-options/{optionId}/booking-notice")
    public Settings update(@PathVariable Long optionId, @Valid @RequestBody Settings input) {
        return service.update(optionId, input);
    }
}
