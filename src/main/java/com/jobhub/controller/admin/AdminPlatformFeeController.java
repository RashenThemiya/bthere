package com.jobhub.controller.admin;

import com.jobhub.dto.market.PlatformFeeRequest;
import com.jobhub.service.market.PlatformFeeService;
import com.jobhub.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPlatformFeeController {
    private final PlatformFeeService service;
    @GetMapping("/provider-types/{serviceTypeId}/platform-fee")
    public PlatformFeeService.Configuration service(@PathVariable Long serviceTypeId){return service.serviceConfiguration(serviceTypeId);}
    @PutMapping("/provider-types/{serviceTypeId}/platform-fee")
    public PlatformFeeService.Configuration updateService(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long serviceTypeId,@Valid @RequestBody PlatformFeeRequest input){
        return service.updateService(actor.id(),serviceTypeId,input);
    }
    @GetMapping("/service-options/{optionId}/platform-fee")
    public PlatformFeeService.Configuration option(@PathVariable Long optionId){return service.optionConfiguration(optionId);}
    @PutMapping("/service-options/{optionId}/platform-fee")
    public PlatformFeeService.Configuration updateOption(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long optionId,@Valid @RequestBody PlatformFeeRequest input){
        return service.updateOption(actor.id(),optionId,input);
    }
}
