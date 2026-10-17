package com.jobhub.controller.admin;

import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.CustomerRequirementsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCustomerRequirementsController {
    private final CustomerRequirementsService service;

    @PostMapping("/customer-requirements") @ResponseStatus(HttpStatus.CREATED)
    public Requirement create(@Valid @RequestBody Definition request) { return service.create(request); }

    @GetMapping("/customer-requirements")
    public List<Requirement> list() { return service.list(); }

    @PutMapping("/customer-requirements/{id}")
    public Requirement update(@PathVariable Long id, @Valid @RequestBody Definition request) {
        return service.update(id, request);
    }

    @PatchMapping("/customer-requirements/{id}/status")
    public Requirement status(@PathVariable Long id, @Valid @RequestBody Status request) {
        return service.status(id, request.status());
    }

    @GetMapping("/customer-submissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
    public Page<Submission> queue(@RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) { return service.queue(status, pageable); }

    @PatchMapping("/customer-submissions/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
    public Submission review(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody Review request) { return service.review(user.id(), id, request); }

    @GetMapping("/customer-eligibility")
    public List<Rule> rules() { return service.listRules(); }

    @PutMapping("/customer-eligibility")
    public Rule global(@Valid @RequestBody AgeRule request) {
        return service.configureRule(new Target("GLOBAL", null, null), request);
    }

    @PutMapping("/provider-types/{serviceTypeId}/customer-eligibility")
    public Rule service(@PathVariable Long serviceTypeId, @Valid @RequestBody AgeRule request) {
        return service.configureRule(new Target("SERVICE", serviceTypeId, null), request);
    }

    @PutMapping("/provider-types/{serviceTypeId}/options/{optionId}/customer-eligibility")
    public Rule option(@PathVariable Long serviceTypeId, @PathVariable Long optionId,
            @Valid @RequestBody AgeRule request) {
        return service.configureRule(new Target("OPTION", serviceTypeId, optionId), request);
    }
}
