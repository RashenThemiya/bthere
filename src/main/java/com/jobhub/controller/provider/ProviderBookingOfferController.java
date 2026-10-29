package com.jobhub.controller.provider;

import com.jobhub.dto.job.BookingRequestsApi.*;
import com.jobhub.entity.job.BookingDispatchEvent;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.BookingRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/providers/me")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderBookingOfferController {
    private final BookingRequestService service;
    @GetMapping("/booking-offers")
    public List<Offer> offers(@AuthenticationPrincipal AuthenticatedUser user){return service.providerOffers(user.id());}
    @GetMapping("/bookings/{jobId}")
    public com.jobhub.dto.job.BookingResponse booking(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long jobId){return service.providerBooking(user.id(),jobId);}
    @PostMapping("/booking-offers/{offerId}/accept")
    public View accept(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long offerId){return service.accept(user.id(),offerId);}
    @PostMapping("/booking-offers/{offerId}/decline")
    public View decline(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long offerId){return service.decline(user.id(),offerId);}
    @PostMapping("/bookings/{jobId}/start")
    public Transition start(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long jobId,@Valid @RequestBody Code input){return service.verifyStartCode(user.id(),jobId,input.code());}
    @PostMapping("/bookings/{jobId}/complete")
    public Transition complete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long jobId){return service.requestCompletion(user.id(),jobId);}
    @GetMapping("/booking-events")
    public List<BookingDispatchEvent> events(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="0") Long after){
        if(after<0)throw new IllegalArgumentException("after must be nonnegative");return service.events(user.id(),after);
    }
    @GetMapping("/booking-requests/{id}/payment-window")
    public PaymentWindow paymentWindow(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {return service.paymentWindow(user.id(),id,true);}
    @PostMapping("/booking-requests/{id}/cancel")
    public View cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {return service.providerCancel(user.id(),id);}
    @PutMapping("/booking-requests/{id}/payment-deadline")
    public View extend(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@Valid @RequestBody PaymentDeadline input) {
        return service.extendPaymentDeadline(user.id(),id,input.confirmationExpiresAt());
    }
}
