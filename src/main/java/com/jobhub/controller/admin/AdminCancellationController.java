package com.jobhub.controller.admin;

import com.jobhub.dto.job.CancellationApi.Cancellation;
import com.jobhub.service.customer.BookingCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCancellationController {
    private final BookingCancellationService service;

    @GetMapping("/bookings/{jobId}/cancellation")
    public Cancellation cancellation(@PathVariable Long jobId) { return service.adminCancellation(jobId); }
}
