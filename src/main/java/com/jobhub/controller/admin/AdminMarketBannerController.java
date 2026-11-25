package com.jobhub.controller.admin;

import com.jobhub.dto.market.BannerApi.AdminBanner;
import com.jobhub.dto.market.BannerApi.Input;
import com.jobhub.dto.market.BannerApi.Order;
import com.jobhub.dto.market.BannerApi.StatusUpdate;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.market.MarketBannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Super Admin: banners per market. Upload the image first with POST /api/v1/uploads/images. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMarketBannerController {
    private final MarketBannerService service;

    @GetMapping("/markets/{marketId}/banners")
    public List<AdminBanner> list(@PathVariable Long marketId) { return service.list(marketId); }

    @PostMapping("/markets/{marketId}/banners")
    public ResponseEntity<AdminBanner> create(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable Long marketId, @Valid @RequestBody Input body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.id(), marketId, body));
    }

    @PutMapping("/markets/{marketId}/banners/order")
    public List<AdminBanner> reorder(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long marketId,
                                     @Valid @RequestBody Order body) {
        return service.reorder(user.id(), marketId, body.bannerIds());
    }

    @GetMapping("/banners/{bannerId}")
    public AdminBanner get(@PathVariable Long bannerId) { return service.get(bannerId); }

    @PutMapping("/banners/{bannerId}")
    public AdminBanner update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long bannerId,
                              @Valid @RequestBody Input body) {
        return service.update(user.id(), bannerId, body);
    }

    @PatchMapping("/banners/{bannerId}/status")
    public AdminBanner status(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long bannerId,
                              @Valid @RequestBody StatusUpdate body) {
        return service.updateStatus(user.id(), bannerId, body.status());
    }

    @DeleteMapping("/banners/{bannerId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long bannerId) {
        service.delete(user.id(), bannerId);
        return ResponseEntity.noContent().build();
    }
}
