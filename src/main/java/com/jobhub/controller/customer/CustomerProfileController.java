package com.jobhub.controller.customer;

import com.jobhub.dto.customer.CustomerProfileRequest;
import com.jobhub.dto.customer.CustomerProfileResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    @GetMapping
    public ResponseEntity<CustomerProfileResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(customerProfileService.get(user.id()));
    }

    @PutMapping
    public ResponseEntity<CustomerProfileResponse> upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CustomerProfileRequest request
    ) {
        return ResponseEntity.ok(customerProfileService.upsert(user.id(), request));
    }
}
