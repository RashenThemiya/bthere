package com.jobhub.controller.customer;

import com.jobhub.dto.customer.ProfileOverviewApi.Overview;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.ProfileOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
