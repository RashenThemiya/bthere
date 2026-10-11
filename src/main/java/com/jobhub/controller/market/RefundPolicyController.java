package com.jobhub.controller.market;

import com.jobhub.dto.market.RefundPolicyResponse;
import com.jobhub.service.market.RefundPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/markets/{marketId}/refund-policy")
@RequiredArgsConstructor
public class RefundPolicyController {
    private final RefundPolicyService service;

    @GetMapping
    public ResponseEntity<RefundPolicyResponse> resolve(
            @PathVariable Long marketId,
            @RequestParam(required = false) Long offeringId) {
        return ResponseEntity.ok(service.resolve(marketId, offeringId));
    }
}
