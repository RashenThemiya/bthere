package com.jobhub.controller.customer;

import com.jobhub.dto.customer.ProfileOverviewApi.Overview;
import com.jobhub.dto.customer.ProfileOverviewApi.PageOf;
import com.jobhub.dto.customer.ProfileOverviewApi.RequestItem;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.ProfileOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileOverviewController {
    private final ProfileOverviewService service;

    /** One call for the Profile page; edits/uploads/verification keep their existing endpoints. */
    @GetMapping("/profile-overview")
    public Overview overview(@AuthenticationPrincipal AuthenticatedUser user,
                             @RequestParam(defaultValue = "" + ProfileOverviewService.DEFAULT_RECENT_LIMIT) int recentLimit) {
        return service.overview(user.id(), recentLimit);
    }

    /** "View all": the customer's booking requests, newest first. status is optional and may repeat or be comma-separated. */
    @GetMapping("/booking-requests")
    public PageOf<RequestItem> bookingRequests(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestParam(required = false) List<String> status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.bookingRequests(user.id(), status, page, size);
    }
}
