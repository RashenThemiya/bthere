package com.jobhub.controller.market;

import com.jobhub.dto.market.CreateMarketRequest;
import com.jobhub.dto.market.MarketResponse;
import com.jobhub.dto.market.UpdateMarketStatusRequest;
import com.jobhub.service.market.MarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @GetMapping
    public ResponseEntity<List<MarketResponse>> listActive() {
        return ResponseEntity.ok(marketService.listActive());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<MarketResponse>> listAll() {
        return ResponseEntity.ok(marketService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MarketResponse> create(
            @Valid @RequestBody CreateMarketRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(marketService.create(request));
    }

    @PutMapping("/{marketId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MarketResponse> update(
            @PathVariable Long marketId,
            @Valid @RequestBody CreateMarketRequest request
    ) {
        return ResponseEntity.ok(marketService.update(marketId, request));
    }

    @PatchMapping("/{marketId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MarketResponse> updateStatus(
            @PathVariable Long marketId,
            @Valid @RequestBody UpdateMarketStatusRequest request
    ) {
        return ResponseEntity.ok(marketService.updateStatus(marketId, request.status()));
    }
}
