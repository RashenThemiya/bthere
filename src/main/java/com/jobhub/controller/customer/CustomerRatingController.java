package com.jobhub.controller.customer;

import com.jobhub.dto.job.RatingApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRatingController {
    private final RatingService service;

    @PostMapping("/bookings/{jobId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingView rate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                           @Valid @RequestBody Rate input) {
        return service.rate(user.id(), jobId, input);
    }

    @GetMapping("/bookings/{jobId}/ratings")
    public JobRatings jobRatings(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.jobRatings(user.id(), jobId);
    }

    @GetMapping("/providers/{providerId}/ratings")
    public ProviderSummary summary(@PathVariable Long providerId, @RequestParam(required = false) Long optionId) {
        return service.summary(providerId, optionId);
    }

    @GetMapping("/providers/{providerId}/reviews")
    public Page<Review> reviews(@PathVariable Long providerId, @RequestParam(required = false) Long optionId,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.reviews(providerId, optionId, page, size);
    }
}
