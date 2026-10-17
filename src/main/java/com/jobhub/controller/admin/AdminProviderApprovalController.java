package com.jobhub.controller.admin;
import com.jobhub.dto.admin.ProviderApprovalApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.AdminProviderApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/v1/admin/provider-approvals")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','VERIFICATION_ADMIN')")
public class AdminProviderApprovalController {
 private final AdminProviderApprovalService service;
 @GetMapping public Page<Summary> list(@RequestParam(defaultValue="PENDING") String status,
     @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
  return service.list(status,PageRequest.of(page,size));
 }
 @GetMapping("/{providerId}") public Detail detail(@PathVariable @Positive Long providerId) { return service.detail(providerId); }
 @PostMapping("/bulk-review") public BulkResponse bulk(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody BulkRequest request) {
  return service.bulk(actor.id(),request);
 }
}
