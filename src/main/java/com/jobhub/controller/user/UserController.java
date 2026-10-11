package com.jobhub.controller.user;

import com.jobhub.dto.auth.CurrentUserResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderMarketLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final ProviderMarketLookupService providerMarketLookupService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(new CurrentUserResponse(
                user.id(),
                user.username(),
                user.email(),
                user.status(),
                user.roles().contains("SERVICE_PROVIDER")
                        ? providerMarketLookupService.findActiveMarketId(user.id())
                        : null,
                user.roles()
        ));
    }
}
