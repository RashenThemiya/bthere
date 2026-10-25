package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.dto.job.CreateBookingRequest;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.exception.CustomerEligibilityException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.job.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerBookingEligibilityTest {
    @Mock CustomerRepository customers;
    @Mock ServiceOptionRepository options;
    @Mock JobRepository jobs;
    @Mock com.jobhub.repository.job.BookingRequestPolicyRepository requestPolicies;
    @Mock CustomerRequirementsService requirements;
    @InjectMocks CustomerBookingService bookings;

    @Test void ineligibleCustomerIsRejectedBeforeAnyBookingIsSaved() {
        when(customers.findByUserId(25L)).thenReturn(Optional.of(new Customer()));
        var option = new ServiceOption(); option.setStatus("ACTIVE");
        option.getServiceTypes().add(new com.jobhub.entity.provider.ServiceTypeOption(3L, 0, "ACTIVE"));
        when(options.findByOptionIdAndServiceProviderTypeId(12L, 3L)).thenReturn(Optional.of(option));
        doThrow(new CustomerEligibilityException(new Eligibility(false,
                List.of(new Reason("PENDING", "NIC", "Verify NIC")))))
                .when(requirements).requireEligible(25L, 3L, 12L);
        var request = new CreateBookingRequest(3L, 12L, null, null, "ONLINE", null, null,
                null, null, null, null, null, null, List.of(), "CARD");
        assertThatThrownBy(() -> bookings.create(25L, request)).isInstanceOf(CustomerEligibilityException.class);
        verifyNoInteractions(jobs);
    }
}
