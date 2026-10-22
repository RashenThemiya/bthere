package com.jobhub.controller.provider;

import com.jobhub.dto.provider.SharedProviderOptionRegistrationResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ServiceOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/providers/me/service-options")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class SharedProviderOptionController {
    private final ServiceOptionService options;

    @GetMapping("/{optionId}/registration")
    public SharedProviderOptionRegistrationResponse registration(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long optionId) {
        return options.sharedRegistration(user.id(), optionId);
    }
}
