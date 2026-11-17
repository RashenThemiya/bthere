package com.jobhub.controller.customer;

import com.jobhub.dto.customer.ContactChangeApi.*;
import com.jobhub.dto.customer.ProfileOverviewApi.Contacts;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.auth.ContactChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Change or verify the signed-in customer's email / phone. Step 1 sends a code, step 2 verifies and saves. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers/me/contacts")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerContactController {
    private final ContactChangeService service;

    @PostMapping("/email/otp")
    public CodeSent requestEmailCode(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody EmailCodeRequest in) {
        return service.requestEmailCode(user.id(), in.email());
    }

    @PostMapping("/email/verify")
    public Contacts verifyEmail(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody EmailVerifyRequest in) {
        return service.verifyEmail(user.id(), in);
    }

    @PostMapping("/phone/otp")
    public CodeSent requestPhoneCode(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PhoneCodeRequest in) {
        return service.requestPhoneCode(user.id(), in.phoneNumber());
    }

    @PostMapping("/phone/verify")
    public Contacts verifyPhone(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PhoneVerifyRequest in) {
        return service.verifyPhone(user.id(), in);
    }
}
