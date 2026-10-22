package com.jobhub.service.provider;

import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import com.jobhub.dto.provider.UpdateProviderOptionsRequest;
import com.jobhub.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SharedProviderRegistrationTest {
    @Test void existingRegistrationIsFoundWithoutCreatingAnotherAssignmentOrApproval() {
        var options=mock(ServiceOptionRepository.class);var registrations=mock(ProviderServiceOptionRepository.class);
        var assignments=mock(ServiceProviderTypeAssignmentRepository.class);var profiles=mock(ProviderProfileService.class);
        var approvals=mock(ProviderServiceApprovalService.class);
        var service=new ServiceOptionService(mock(ServiceProviderTypeRepository.class),options,registrations,
                mock(ProviderServiceLanguageRepository.class),assignments,profiles,approvals,mock(ServiceCustomFieldService.class));
        var provider=new ServiceProvider();provider.setServiceProviderId(15L);
        when(profiles.findByUserId(5L)).thenReturn(provider);
        var registration=new ProviderServiceOption();registration.setProviderOptionId(77L);registration.setAssignmentId(10L);
        registration.setOptionId(12L);registration.setVerificationStatus("APPROVED");
        when(registrations.findByProviderIdAndOptionId(15L,12L)).thenReturn(Optional.of(registration));
        var source=new ServiceProviderTypeAssignment();source.setAssignmentId(10L);source.setServiceProviderTypeId(1L);
        when(assignments.findByAssignmentIdAndServiceProviderId(10L,15L)).thenReturn(Optional.of(source));
        var result=service.sharedRegistration(5L,12L);
        assertThat(result.assignmentId()).isEqualTo(10L);assertThat(result.providerOptionId()).isEqualTo(77L);
        assertThat(result.verificationStatus()).isEqualTo("APPROVED");
        verify(registrations,never()).save(any());verifyNoInteractions(approvals,options);
    }
}
