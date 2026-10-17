package com.jobhub.payments;
import com.jobhub.dto.customer.WalletApi.Capabilities;
import com.jobhub.repository.finance.PaymentGatewaySettingsRepository;
public class ConfiguredPaymentGateway implements PaymentGateway {
 private final PaymentGatewaySettingsRepository settings;
 public ConfiguredPaymentGateway(PaymentGatewaySettingsRepository settings) { this.settings=settings; }
 private PaymentGateway current() {
  String name=settings.findById(1L).map(s -> s.getProvider()).orElse("DISABLED");
  return "MOCK".equals(name)?new MockPaymentGateway():new UnconfiguredPaymentGateway(name);
 }
 public String name() { return current().name(); }
 public boolean isMock() { return current().isMock(); }
 public Capabilities capabilities() { return current().capabilities(); }
 public void requireEnabled() { current().requireEnabled(); }
 public String start(Request request) { return current().start(request); }
 public String simulate(String outcome) { return current().simulate(outcome); }
}
