package com.jobhub.controller.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderAvailabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers/me/services/{assignmentId}")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderAvailabilityController {

    private final ProviderAvailabilityService service;

    @GetMapping("/availability")
    public ResponseEntity<ProviderScheduleResponse> getSchedule(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getSchedule(user.id(), assignmentId));
    }

    @PutMapping("/availability")
    public ResponseEntity<ProviderScheduleResponse> replaceSchedule(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody ProviderScheduleRequest request) {
        return ResponseEntity.ok(service.replaceSchedule(user.id(), assignmentId, request));
    }

    @GetMapping("/unavailable-dates")
    public ResponseEntity<List<ProviderUnavailableDateResponse>> getUnavailableDates(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getUnavailableDates(user.id(), assignmentId));
    }

    @PostMapping("/unavailable-dates")
    public ResponseEntity<ProviderUnavailableDateResponse> addUnavailableDate(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody ProviderUnavailableDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addUnavailableDate(user.id(), assignmentId, request));
    }

    @DeleteMapping("/unavailable-dates/{unavailableDateId}")
    public ResponseEntity<Void> deleteUnavailableDate(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @PathVariable Long unavailableDateId) {
        service.deleteUnavailableDate(user.id(), assignmentId, unavailableDateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options/{optionId}/delivery-modes/{deliveryMode}/service-areas")
    public ResponseEntity<List<ProviderServiceAreaResponse>> getServiceAreas(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @PathVariable Long optionId, @PathVariable String deliveryMode) {
        return ResponseEntity.ok(service.getServiceAreas(
                user.id(), assignmentId, optionId, deliveryMode));
    }

    @PutMapping("/options/{optionId}/delivery-modes/{deliveryMode}/service-areas")
    public ResponseEntity<List<ProviderServiceAreaResponse>> replaceServiceAreas(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @PathVariable Long optionId, @PathVariable String deliveryMode,
            @RequestBody @NotEmpty List<@Valid ProviderServiceAreaRequest> requests) {
        return ResponseEntity.ok(service.replaceServiceAreas(
                user.id(), assignmentId, optionId, deliveryMode, requests));
    }

    /** @deprecated Use the option and delivery-mode specific endpoint. */
    @Deprecated
    @GetMapping("/service-areas")
    public ResponseEntity<List<ProviderServiceAreaResponse>> getLegacyServiceAreas(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getLegacyServiceAreas(user.id(), assignmentId));
    }

    /** @deprecated Use the option and delivery-mode specific endpoint. */
    @Deprecated
    @PutMapping("/service-areas")
    public ResponseEntity<List<ProviderServiceAreaResponse>> replaceLegacyServiceAreas(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @RequestBody @NotEmpty List<@Valid ProviderServiceAreaRequest> requests) {
        return ResponseEntity.ok(service.replaceLegacyServiceAreas(
                user.id(), assignmentId, requests));
    }
}
