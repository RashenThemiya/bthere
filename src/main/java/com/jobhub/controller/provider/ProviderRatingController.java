package com.jobhub.controller.provider;

import com.jobhub.dto.job.RatingApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/providers/me")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderRatingController {
    private final RatingService service;

    @GetMapping("/ratings")
    public ProviderSummary summary(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false) Long optionId) {
        return service.mySummary(user.id(), optionId);
    }

    @GetMapping("/reviews")
    public Page<Review> reviews(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false) Long optionId,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.myReviews(user.id(), optionId, page, size);
    }
}
