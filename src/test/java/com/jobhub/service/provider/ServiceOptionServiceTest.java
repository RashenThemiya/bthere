package com.jobhub.service.provider;

import com.jobhub.dto.provider.CreateServiceOptionRequest;
import com.jobhub.dto.provider.ServiceOptionResponse;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.repository.provider.ProviderServiceLanguageRepository;
import com.jobhub.repository.provider.ProviderServiceOptionRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceOptionServiceTest {

    @Mock private ServiceProviderTypeRepository typeRepository;
    @Mock private ServiceOptionRepository optionRepository;
    @Mock private ProviderServiceOptionRepository providerOptionRepository;
    @Mock private ProviderServiceLanguageRepository languageRepository;
    @Mock private ServiceProviderTypeAssignmentRepository assignmentRepository;
    @Mock private ProviderProfileService profileService;
    @Mock private ProviderServiceApprovalService approvalService;

    private ServiceOptionService service;

    @BeforeEach
    void setUp() {
        service = new ServiceOptionService(typeRepository, optionRepository,
                providerOptionRepository, languageRepository, assignmentRepository,
                profileService, approvalService);
        when(typeRepository.findById(1L)).thenReturn(Optional.of(new ServiceProviderType()));
        when(optionRepository.existsByServiceProviderTypeIdAndCodeIgnoreCase(1L, "HOME_CARE"))
                .thenReturn(false);
    }

    @Test
    void defaultsProviderSelectionModeToOpenRequestWhenOmitted() {
        stubSave();
        ServiceOptionResponse response = service.create(1L, request(null));

        assertThat(response.providerSelectionMode()).isEqualTo("OPEN_REQUEST");
    }

    @Test
    void acceptsDirectRequestMode() {
        stubSave();
        ServiceOptionResponse response = service.create(1L, request("direct_request", null));

        assertThat(response.providerSelectionMode()).isEqualTo("DIRECT_REQUEST");
    }

    @Test
    void defaultsPricingOwnerToAdminWhenOmitted() {
        stubSave();
        ServiceOptionResponse response = service.create(1L, request(null, null));

        assertThat(response.pricingOwner()).isEqualTo("ADMIN");
    }

    @Test
    void acceptsProviderPricingOwner() {
        stubSave();
        ServiceOptionResponse response = service.create(1L, request(null, "provider"));

        assertThat(response.pricingOwner()).isEqualTo("PROVIDER");
    }

    @Test
    void defaultsToAllPaymentMethodsWhenOmitted() {
        stubSave();
        ServiceOptionResponse response = service.create(1L, request(null, null, null));

        assertThat(response.allowedPaymentMethods()).containsExactlyInAnyOrder(
                "CASH", "CARD", "BANK_TRANSFER", "WALLET", "EZ_CASH", "KOKO");
    }

    @Test
    void acceptsConfiguredPaymentMethods() {
        stubSave();
        ServiceOptionResponse response = service.create(
                1L, request(null, null, Set.of("cash", "card")));

        assertThat(response.allowedPaymentMethods()).containsExactlyInAnyOrder("CASH", "CARD");
    }

    @Test
    void rejectsUnsupportedPaymentMethod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(
                        1L, request(null, null, Set.of("CRYPTO"))))
                .withMessage("Payment method must be CASH, CARD, BANK_TRANSFER, WALLET, "
                        + "EZ_CASH or KOKO");
    }

    @Test
    void rejectsUnsupportedPricingOwner() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(1L, request(null, "CUSTOMER")))
                .withMessage("Pricing owner must be ADMIN or PROVIDER");
    }

    @Test
    void rejectsUnsupportedProviderSelectionMode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(1L, request("BOTH", null)))
                .withMessage("Provider selection mode must be OPEN_REQUEST or DIRECT_REQUEST");
    }

    private CreateServiceOptionRequest request(String providerSelectionMode) {
        return request(providerSelectionMode, null);
    }

    private CreateServiceOptionRequest request(
            String providerSelectionMode, String pricingOwner) {
        return request(providerSelectionMode, pricingOwner, null);
    }

    private CreateServiceOptionRequest request(
            String providerSelectionMode, String pricingOwner,
            Set<String> allowedPaymentMethods) {
        return new CreateServiceOptionRequest("HOME_CARE", "Home Care", null, 1,
                "TIME_BASED", null, true, Set.of("PROVIDER_TO_CUSTOMER"),
                "ONE_TO_ONE", 1, 1, "ONE_AT_A_TIME", providerSelectionMode, pricingOwner,
                allowedPaymentMethods);
    }

    private void stubSave() {
        when(optionRepository.save(any(ServiceOption.class))).thenAnswer(invocation -> {
            ServiceOption option = invocation.getArgument(0);
            option.setOptionId(10L);
            return option;
        });
    }
}
