package com.jobhub.controller.admin;

import com.jobhub.dto.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.service.provider.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Option-owned configuration; service links are navigation, not independent copies. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/service-options/{optionId}")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSharedOptionConfigurationController {
    private final ServiceOptionRepository options;
    private final ServiceOptionService service;
    private final ServiceCustomFieldService fields;
    private final SharedOptionDocumentService documents;
    private final MapMarkerRuleService markers;

    @GetMapping
    public ServiceOptionResponse get(@PathVariable Long optionId) {
        return service.list(owner(optionId), true).stream().filter(o -> optionId.equals(o.optionId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
    }

    @PutMapping
    public ServiceOptionResponse update(@PathVariable Long optionId, @Valid @RequestBody CreateServiceOptionRequest request) {
        return service.update(owner(optionId), optionId, request);
    }

    @GetMapping("/custom-fields")
    public List<ServiceCustomFieldResponse> fields(@PathVariable Long optionId) {
        return fields.listForOption(owner(optionId), optionId, true);
    }

    @PostMapping("/custom-fields")
    public ResponseEntity<ServiceCustomFieldResponse> createField(@PathVariable Long optionId,
            @Valid @RequestBody ServiceCustomFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fields.create(owner(optionId), scoped(optionId, request, List.of(optionId))));
    }

    @PutMapping("/custom-fields/{fieldId}")
    public ServiceCustomFieldResponse updateField(@PathVariable Long optionId, @PathVariable Long fieldId,
            @Valid @RequestBody ServiceCustomFieldRequest request) {
        var existing = requireField(optionId, fieldId);
        return fields.update(owner(optionId), fieldId, scoped(optionId, request, existing.optionIds()));
    }

    @PatchMapping("/custom-fields/{fieldId}/status")
    public ServiceCustomFieldResponse fieldStatus(@PathVariable Long optionId, @PathVariable Long fieldId,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        requireField(optionId, fieldId);
        return fields.updateStatus(owner(optionId), fieldId, request.status());
    }

    @GetMapping("/document-requirements")
    public List<ServiceDocumentRequirementResponse> documents(@PathVariable Long optionId) { return documents.get(optionId); }

    @PutMapping("/document-requirements")
    public List<ServiceDocumentRequirementResponse> documents(@PathVariable Long optionId,
            @Valid @RequestBody OptionDocumentRequirementsRequest request) { return documents.replace(optionId, request); }

    @GetMapping("/map-marker-rules")
    public List<MapMarkerRuleResponse> markers(@PathVariable Long optionId) { return markers.list(owner(optionId), optionId); }

    @PostMapping("/map-marker-rules")
    public ResponseEntity<MapMarkerRuleResponse> marker(@PathVariable Long optionId,
            @Valid @RequestBody MapMarkerRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(markers.create(owner(optionId), optionId, request));
    }

    @PutMapping("/map-marker-rules/{ruleId}")
    public MapMarkerRuleResponse marker(@PathVariable Long optionId, @PathVariable Long ruleId,
            @Valid @RequestBody MapMarkerRuleRequest request) {
        return markers.update(owner(optionId), optionId, ruleId, request);
    }

    @DeleteMapping("/map-marker-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMarker(@PathVariable Long optionId, @PathVariable Long ruleId) {
        markers.delete(owner(optionId), optionId, ruleId);
    }

    private ServiceCustomFieldResponse requireField(Long optionId, Long fieldId) {
        return fields.listForOption(owner(optionId), optionId, true).stream().filter(f -> fieldId.equals(f.fieldId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Field not found for shared option"));
    }

    private ServiceCustomFieldRequest scoped(Long optionId, ServiceCustomFieldRequest request, List<Long> defaults) {
        if (request.optionId() == null && request.optionIds() == null) return request.withOptionIds(defaults);
        if (request.optionId() != null && !optionId.equals(request.optionId())
                || request.optionIds() != null && !request.optionIds().contains(optionId)) {
            throw new IllegalArgumentException("Field must apply to the option in the URL");
        }
        return request;
    }

    private Long owner(Long id) {
        return options.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service option not found"))
                .getServiceProviderTypeId();
    }
}
