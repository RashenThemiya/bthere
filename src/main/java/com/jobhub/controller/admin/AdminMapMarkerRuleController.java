package com.jobhub.controller.admin;

import com.jobhub.dto.provider.*;
import com.jobhub.service.provider.MapMarkerRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}/map-marker-rules")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMapMarkerRuleController {
    private final MapMarkerRuleService service;

    @GetMapping
    public List<MapMarkerRuleResponse> list(@PathVariable Long serviceTypeId, @PathVariable Long optionId) {
        return service.list(serviceTypeId, optionId);
    }
    @PostMapping
    public ResponseEntity<MapMarkerRuleResponse> create(@PathVariable Long serviceTypeId, @PathVariable Long optionId,
            @Valid @RequestBody MapMarkerRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(serviceTypeId, optionId, request));
    }
    @PutMapping("/{ruleId}")
    public MapMarkerRuleResponse update(@PathVariable Long serviceTypeId, @PathVariable Long optionId,
            @PathVariable Long ruleId, @Valid @RequestBody MapMarkerRuleRequest request) {
        return service.update(serviceTypeId, optionId, ruleId, request);
    }
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable Long serviceTypeId, @PathVariable Long optionId, @PathVariable Long ruleId) {
        service.delete(serviceTypeId, optionId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
