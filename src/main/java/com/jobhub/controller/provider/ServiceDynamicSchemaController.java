package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ServiceDynamicSchemaResponse;
import com.jobhub.service.provider.ServiceCustomFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/provider-types/{serviceTypeId}")
public class ServiceDynamicSchemaController {
    private final ServiceCustomFieldService service;

    @GetMapping("/onboarding-schema")
    public ResponseEntity<ServiceDynamicSchemaResponse> onboarding(
            @PathVariable Long serviceTypeId) {
        return ResponseEntity.ok(service.schema(
                serviceTypeId, ServiceCustomFieldService.PROVIDER_REQUIREMENT));
    }

    @GetMapping("/booking-schema")
    public ResponseEntity<ServiceDynamicSchemaResponse> booking(
            @PathVariable Long serviceTypeId,
            @RequestParam(required = false) Long optionId) {
        return ResponseEntity.ok(service.schema(
                serviceTypeId, ServiceCustomFieldService.BOOKING_FIELD, optionId));
    }
}
