package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerProfileRequest;
import com.jobhub.dto.customer.CustomerProfileResponse;
import com.jobhub.entity.customer.Customer;
import com.jobhub.repository.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerProfileService customerProfileService;

    @Test
    void createsCompletedCustomerProfile() {
        CustomerProfileRequest request = new CustomerProfileRequest(
                "Jane",
                "Doe",
                null,
                "10 Main Street",
                null,
                "Colombo",
                "Colombo",
                "Western",
                "00100",
                "Sri Lanka"
        );
        when(customerRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setCustomerId(12L);
            return customer;
        });

        CustomerProfileResponse response = customerProfileService.upsert(7L, request);

        assertThat(response.customerId()).isEqualTo(12L);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.profileCompleted()).isTrue();
    }
}
