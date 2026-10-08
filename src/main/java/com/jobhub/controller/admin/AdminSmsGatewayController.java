package com.jobhub.controller.admin;

import com.jobhub.dto.admin.SmsGatewaySettingsRequest;
import com.jobhub.dto.admin.SmsGatewaySettingsResponse;
import com.jobhub.service.admin.SmsGatewaySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings/sms")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSmsGatewayController {

    private final SmsGatewaySettingsService settingsService;

    @GetMapping
    public ResponseEntity<SmsGatewaySettingsResponse> get() {
        return ResponseEntity.ok(settingsService.get());
    }

    @PutMapping
    public ResponseEntity<SmsGatewaySettingsResponse> update(
            @Valid @RequestBody SmsGatewaySettingsRequest request
    ) {
        return ResponseEntity.ok(settingsService.update(request));
    }
}
