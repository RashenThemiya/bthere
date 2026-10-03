package com.jobhub.controller.market;

import com.jobhub.dto.market.CreateMarketRequest;
import com.jobhub.dto.market.MarketResponse;
import com.jobhub.service.market.MarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MarketResponse> create(
            @Valid @RequestBody CreateMarketRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(marketService.create(request));
    }
}
