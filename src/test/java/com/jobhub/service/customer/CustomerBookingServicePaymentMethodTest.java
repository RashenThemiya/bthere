package com.jobhub.service.customer;

import com.jobhub.entity.provider.ServiceOption;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CustomerBookingServicePaymentMethodTest {

    @Test
    void defaultsOldBookingRequestsToCashWhenAllowed() {
        ServiceOption option = optionWith("CASH", "CARD");

        assertThat(CustomerBookingService.resolvePaymentMethod(option, null)).isEqualTo("CASH");
    }

    @Test
    void acceptsAndNormalizesAllowedMethod() {
        ServiceOption option = optionWith("BANK_TRANSFER", "WALLET");

        assertThat(CustomerBookingService.resolvePaymentMethod(option, "bank transfer"))
                .isEqualTo("BANK_TRANSFER");
    }

    @Test
    void acceptsEzCashAndKoko() {
        ServiceOption option = optionWith("EZ_CASH", "KOKO");

        assertThat(CustomerBookingService.resolvePaymentMethod(option, "ez cash"))
                .isEqualTo("EZ_CASH");
        assertThat(CustomerBookingService.resolvePaymentMethod(option, "koko"))
                .isEqualTo("KOKO");
    }

    @Test
    void rejectsMethodNotEnabledForOption() {
        ServiceOption option = optionWith("CARD");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> CustomerBookingService.resolvePaymentMethod(option, "CASH"))
                .withMessage("Payment method is not allowed for this service option");
    }

    @Test
    void requiresSelectionWhenCashIsDisabled() {
        ServiceOption option = optionWith("CARD", "WALLET");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> CustomerBookingService.resolvePaymentMethod(option, null))
                .withMessage("Select a payment method allowed for this service option");
    }

    private ServiceOption optionWith(String... methods) {
        ServiceOption option = new ServiceOption();
        option.setAllowedPaymentMethods(new LinkedHashSet<>(Set.of(methods)));
        return option;
    }
}
