package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ServiceOptionResponse;
import com.jobhub.service.provider.ServiceOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/provider-types/{serviceTypeId}/options")
public class ServiceOptionController {
    private final ServiceOptionService service;

    @GetMapping
    public ResponseEntity<List<ServiceOptionResponse>> list(@PathVariable Long serviceTypeId) {
        return ResponseEntity.ok(service.list(serviceTypeId, false));
    }
}
