package com.jobhub.service.finance;
import com.jobhub.payments.*;
import com.jobhub.entity.finance.PaymentGatewaySettings;
import com.jobhub.repository.finance.PaymentGatewaySettingsRepository;
import com.jobhub.controller.admin.AdminPaymentGatewayController;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PaymentGatewayConfigurationTest {
 @Test void persistedSelectionChangesWithoutRestartAndDefaultsOff() {
  var repo=mock(PaymentGatewaySettingsRepository.class);
  var gateway=new PaymentGatewayConfiguration().paymentGateway(repo);
  when(repo.findById(1L)).thenReturn(Optional.empty());
  assertThat(gateway.capabilities().topups()).isFalse();
  var setting=new PaymentGatewaySettings(); setting.setProvider("MOCK");
  when(repo.findById(1L)).thenReturn(Optional.of(setting));
  assertThat(gateway.isMock()).isTrue(); assertThat(gateway.simulate("SUCCESS")).isEqualTo("SUCCESS");
  setting.setProvider("PAYHERE");
  assertThat(gateway.isMock()).isFalse();
  assertThatThrownBy(gateway::requireEnabled).isInstanceOf(com.jobhub.exception.PaymentUnavailableException.class);
  assertThatThrownBy(() -> gateway.simulate("SUCCESS")).isInstanceOf(com.jobhub.exception.PaymentUnavailableException.class);
 }
 @Test void administratorSelectionIsPersisted() {
  var repo=mock(PaymentGatewaySettingsRepository.class);
  when(repo.findById(1L)).thenReturn(Optional.empty());
  when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
  var controller=new AdminPaymentGatewayController(repo,mock(com.jobhub.service.audit.AuditService.class));
  assertThat(controller.get().provider()).isEqualTo("DISABLED");
  assertThat(controller.update(new AdminPaymentGatewayController.Update("MOCK"),new com.jobhub.security.AuthenticatedUser(1L,"admin",null,"ACTIVE",java.util.List.of("SUPER_ADMIN"))).testMode()).isTrue();
  verify(repo).save(argThat(s -> s.getProvider().equals("MOCK")));
 }
}

