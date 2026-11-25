package com.jobhub.controller.market;

import com.jobhub.dto.market.BannerApi.MarketBanners;
import com.jobhub.service.market.MarketBannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/** Mobile apps: live banners of a market. Public (no Authorization header needed). */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/markets/{marketId}/banners")
public class MarketBannerController {
    private final MarketBannerService service;

    @GetMapping
    public ResponseEntity<MarketBanners> live(
            @PathVariable Long marketId,
            @RequestParam(defaultValue = "CUSTOMER") String audience) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(service.live(marketId, audience));
    }
}
