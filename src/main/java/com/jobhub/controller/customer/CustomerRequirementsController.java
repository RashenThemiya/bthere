package com.jobhub.controller.customer;

import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.CustomerRequirementsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRequirementsController {
    private final CustomerRequirementsService service;

    @GetMapping("/requirements")
    public List<FormItem> form(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long serviceTypeId, @RequestParam(required = false) Long optionId) {
        return service.form(user.id(), serviceTypeId, optionId);
    }

    @PutMapping("/requirement-submissions")
    public List<Submission> submit(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Submit request) {
        return service.submit(user.id(), request);
    }

    @GetMapping("/eligibility")
    public Eligibility eligibility(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long serviceTypeId, @RequestParam(required = false) Long optionId) {
        return service.eligibility(user.id(), serviceTypeId, optionId);
    }
}
