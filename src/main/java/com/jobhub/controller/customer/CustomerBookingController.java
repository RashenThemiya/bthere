package com.jobhub.controller.customer;

import com.jobhub.dto.job.BookingResponse;
import com.jobhub.dto.job.CreateBookingRequest;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.CustomerBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/me/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerBookingController {

    private final CustomerBookingService service;

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.id(), request));
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.list(user.id()));
    }
}
