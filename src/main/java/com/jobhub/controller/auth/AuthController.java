package com.jobhub.controller.auth;

import com.jobhub.dto.auth.GoogleLoginRequest;
import com.jobhub.dto.auth.EmailResendRequest;
import com.jobhub.dto.auth.EmailVerificationRequest;
import com.jobhub.dto.auth.LoginRequest;
import com.jobhub.dto.auth.LoginResponse;
import com.jobhub.dto.auth.OtpRequest;
import com.jobhub.dto.auth.OtpRequestResponse;
import com.jobhub.dto.auth.OtpVerifyRequest;
import com.jobhub.dto.auth.RegisterRequest;
import com.jobhub.dto.auth.RegisterResponse;
import com.jobhub.service.auth.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/email/verify")
    public ResponseEntity<LoginResponse> verifyEmail(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authenticationService.verifyEmail(request, httpRequest));
    }

    @PostMapping("/email/resend")
    public ResponseEntity<OtpRequestResponse> resendEmailVerification(
            @Valid @RequestBody EmailResendRequest request
    ) {
        return ResponseEntity.accepted()
                .body(authenticationService.resendEmailVerification(request));
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponse> requestOtp(
            @Valid @RequestBody OtpRequest request
    ) {
        return ResponseEntity.accepted()
                .body(authenticationService.requestOtp(request));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<LoginResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authenticationService.verifyOtp(request, httpRequest));
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authenticationService.googleLogin(request, httpRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authenticationService.login(request, httpRequest));
    }
}
