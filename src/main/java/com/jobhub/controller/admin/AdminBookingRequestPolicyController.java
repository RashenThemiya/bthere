package com.jobhub.controller.admin;
import com.jobhub.dto.job.BookingRequestsApi.Policy;
import com.jobhub.service.customer.BookingRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/service-options/{optionId}/booking-request-policy")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBookingRequestPolicyController {
    private final BookingRequestService service;
    @GetMapping public Policy get(@PathVariable Long optionId){return service.policy(optionId);}
    @PutMapping public Policy update(@PathVariable Long optionId,@Valid @RequestBody Policy input){return service.setPolicy(optionId,input);}
}
