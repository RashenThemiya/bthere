package com.jobhub.controller.customer;
import com.jobhub.service.customer.BookingRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor
public class PayHereBookingNotificationController {
    private final BookingRequestService service;
    @PostMapping(value="/api/v1/payments/payhere/booking-notify",consumes="application/x-www-form-urlencoded")
    public void notify(@RequestParam org.springframework.util.MultiValueMap<String,String> input){
        if(input.values().stream().anyMatch(v -> v.size()!=1))throw new IllegalArgumentException("Duplicate payment notification fields");
        service.notification(input.toSingleValueMap());
    }
}
