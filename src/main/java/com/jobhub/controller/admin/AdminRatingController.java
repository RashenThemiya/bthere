package com.jobhub.controller.admin;

import com.jobhub.dto.job.RatingApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRatingController {
    private final RatingService service;

    @GetMapping("/ratings")
    public Page<AdminRating> search(@RequestParam(required = false) Long providerId,
                                    @RequestParam(required = false) Long optionId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.adminSearch(providerId, optionId, status, page, size);
    }

    @GetMapping("/providers/{providerId}/ratings")
    public ProviderSummary summary(@PathVariable Long providerId, @RequestParam(required = false) Long optionId) {
        return service.summary(providerId, optionId);
    }

    @PatchMapping("/ratings/{ratingId}/status")
    public AdminRating moderate(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long ratingId,
                                @Valid @RequestBody Moderate input) {
        return service.moderate(actor.id(), ratingId, input);
    }
}
