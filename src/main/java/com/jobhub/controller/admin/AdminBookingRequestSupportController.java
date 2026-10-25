package com.jobhub.controller.admin;
import com.jobhub.dto.job.BookingRequestsApi.View;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.BookingRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class AdminBookingRequestSupportController {
    private final BookingRequestService service;
    public record Evidence(@NotBlank @Size(min=10,max=1000) String reason) {}
    @PostMapping("/bookings/{jobId}/verification-codes/reset") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void reset(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long jobId,@Valid @RequestBody Evidence input){
        service.resetCode(actor.id(),jobId,input.reason());
    }
    @PostMapping("/booking-requests/{requestId}/resolve-unpaid") @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN')")
    public View unpaid(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long requestId,@Valid @RequestBody Evidence input){
        return service.resolveUnpaid(actor.id(),requestId,input.reason());
    }
}
