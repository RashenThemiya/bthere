package com.jobhub.controller.admin;
import com.jobhub.entity.finance.PaymentGatewaySettings;
import com.jobhub.repository.finance.PaymentGatewaySettingsRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/settings/payments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPaymentGatewayController {
 private final PaymentGatewaySettingsRepository settings;
 private final com.jobhub.service.audit.AuditService audit;
 public record Update(@NotNull @Pattern(regexp="DISABLED|MOCK|PAYHERE") String provider) {}
 public record View(String provider,boolean configured,boolean testMode) {}
 @GetMapping @Transactional(readOnly=true)
 public View get() { return view(settings.findById(1L).orElseGet(PaymentGatewaySettings::new)); }
 @PutMapping @Transactional
 public View update(@Valid @RequestBody Update request, @org.springframework.security.core.annotation.AuthenticationPrincipal com.jobhub.security.AuthenticatedUser actor) {
  var value=settings.findById(1L).orElseGet(PaymentGatewaySettings::new);
  var old=view(value); value.setProvider(request.provider()); var result=view(settings.save(value));
  audit.record(actor.id(),"PAYMENT_GATEWAY_CHANGED","PAYMENT_GATEWAY_SETTINGS",1L,old,result); return result;
 }
 private View view(PaymentGatewaySettings s) { return new View(s.getProvider(),"MOCK".equals(s.getProvider()),"MOCK".equals(s.getProvider())); }
}

