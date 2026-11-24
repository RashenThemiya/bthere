package com.jobhub.controller.admin;

import com.jobhub.dto.job.CancellationApi.AutoCancelSettings;
import com.jobhub.dto.job.CancellationApi.AutoCancelUpdate;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.BookingAutoCancelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Super Admin: grace period before unstarted paid bookings are cancelled automatically, per service option. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAutoCancelController {
    private final BookingAutoCancelService service;

    @GetMapping("/service-options/{optionId}/auto-cancel-settings")
    public AutoCancelSettings settings(@PathVariable Long optionId) { return service.settings(optionId); }

    /** autoCancelAfterMinutes 5-1440, or null for the server default. Applies to existing bookings too. */
    @PutMapping("/service-options/{optionId}/auto-cancel-settings")
    public AutoCancelSettings update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long optionId,
                                     @Valid @RequestBody AutoCancelUpdate body) {
        return service.updateSettings(user.id(), optionId, body);
    }
}
