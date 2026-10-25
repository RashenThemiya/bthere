package com.jobhub.controller.customer;

import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.CustomerDiscoveryService;
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
public class CustomerDiscoveryController {
    private final CustomerDiscoveryService service;
    @GetMapping("/services")
    public List<ServiceCard> services(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam Long marketId) {
        return service.services(user.id(), marketId);
    }
    @GetMapping("/services/{serviceTypeId}/options")
    public List<OptionCard> options(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam Long marketId,
            @PathVariable Long serviceTypeId) { return service.options(user.id(), marketId, serviceTypeId); }
    @GetMapping("/booking-context")
    public Context context(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam Long marketId,
            @RequestParam Long serviceTypeId, @RequestParam Long optionId,
            @RequestParam(required = false) java.math.BigDecimal pricingLatitude,
            @RequestParam(required = false) java.math.BigDecimal pricingLongitude) {
        return service.context(user.id(), marketId, serviceTypeId, optionId, pricingLatitude, pricingLongitude);
    }
    @PostMapping("/provider-search")
    public Results search(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Search request) {
        return service.search(user.id(), request);
    }
}
