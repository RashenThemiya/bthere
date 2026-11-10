package com.jobhub.controller.admin;

import com.jobhub.dto.job.CancellationApi.Penalty;
import com.jobhub.dto.job.CancellationApi.Reactivate;
import com.jobhub.dto.job.CancellationApi.Waive;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.ProviderPenaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Provider cancellation penalties: list, waive, and lift a cancellation suspension. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminProviderPenaltyController {
    private final ProviderPenaltyService service;

    @GetMapping("/provider-penalties")
    public Page<Penalty> list(@RequestParam(required = false) Long providerId, @RequestParam(required = false) String status,
                              @PageableDefault(size = 20, sort = "penaltyId", direction = org.springframework.data.domain.Sort.Direction.DESC)
                              Pageable pageable) {
        return service.list(providerId, status, pageable);
    }

    @GetMapping("/provider-penalties/{penaltyId}")
    public Penalty get(@PathVariable Long penaltyId) { return service.get(penaltyId); }

    @PostMapping("/provider-penalties/{penaltyId}/waive")
    public Penalty waive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long penaltyId,
                         @Valid @RequestBody Waive body) {
        return service.waive(user.id(), penaltyId, body.reason());
    }

    @PostMapping("/providers/{providerId}/reactivate")
    public Map<String, Object> reactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long providerId,
                                          @RequestBody(required = false) @Valid Reactivate body) {
        return service.reactivate(user.id(), providerId, body == null ? null : body.note());
    }
}
