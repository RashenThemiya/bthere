package com.jobhub.controller.admin;

import com.jobhub.dto.admin.BookingMonitorApi.*;
import com.jobhub.service.admin.BookingMonitorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Administrative monitoring only; lifecycle changes use the existing audited support APIs. */
@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/v1/admin/booking-monitor")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBookingMonitorController {
    private final BookingMonitorService service;
    @GetMapping("/summary")
    public Summary summary() {return service.summary();}
    @GetMapping("/requests")
    public Page<RequestRow> requests(@Valid @ModelAttribute RequestFilter filter,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.requests(filter,page,size);
    }
    @GetMapping("/requests/{requestId}")
    public RequestDetail request(@PathVariable @Positive Long requestId) {return service.request(requestId);}
    @GetMapping("/bookings")
    public Page<BookingRow> bookings(@Valid @ModelAttribute BookingFilter filter,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.bookings(filter,page,size);
    }
    @GetMapping("/bookings/{jobId}")
    public BookingDetail booking(@PathVariable @Positive Long jobId) {return service.booking(jobId);}
    @GetMapping("/offers")
    public Page<OfferRow> offers(@Valid @ModelAttribute OfferFilter filter,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.offers(filter,page,size);
    }
    @GetMapping("/payment-notices")
    public Page<PaymentRow> payments(@Valid @ModelAttribute PaymentFilter filter,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.payments(filter,page,size);
    }
    @GetMapping("/transactions")
    public Page<TransactionRow> transactions(@Valid @ModelAttribute TransactionFilter filter,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.transactions(filter,page,size);
    }
    @GetMapping("/transactions/{operationId}")
    public TransactionRow transaction(@PathVariable @Positive Long operationId) {return service.transaction(operationId);}
    @GetMapping("/transactions/{operationId}/entries")
    public Page<LedgerRow> entries(@PathVariable @Positive Long operationId,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.entries(operationId,page,size);
    }
    @GetMapping("/events")
    public Page<EventRow> events(@Valid @ModelAttribute EventFilter filter,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.events(filter,page,size);
    }
}
