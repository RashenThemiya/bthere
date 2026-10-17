package com.jobhub.payments;
import com.jobhub.repository.finance.PaymentGatewaySettingsRepository;
import org.springframework.context.annotation.*;
@Configuration
public class PaymentGatewayConfiguration {
 @Bean public PaymentGateway paymentGateway(PaymentGatewaySettingsRepository settings) { return new ConfiguredPaymentGateway(settings); }
}
