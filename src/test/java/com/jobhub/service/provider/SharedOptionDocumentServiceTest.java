package com.jobhub.service.provider;

import com.jobhub.dto.provider.OptionDocumentRequirementsRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SharedOptionDocumentServiceTest {
    @Test void replacingOneOptionsDocumentsPreservesOtherOptionsAndRefreshesItsProviders() {
        var options = mock(ServiceOptionRepository.class);
        var requirements = mock(ServiceTypeDocumentRequirementRepository.class);
        var types = mock(DocumentTypeRepository.class);
        var registrations = mock(ProviderServiceOptionRepository.class);
        var assignments = mock(ServiceProviderTypeAssignmentRepository.class);
        var approvals = mock(ProviderServiceApprovalService.class);
        var service = new SharedOptionDocumentService(options,requirements,types,registrations,assignments,approvals);
        var option = new ServiceOption(); option.setOptionId(12L); option.setServiceProviderTypeId(1L);
        when(options.findLockedById(12L)).thenReturn(Optional.of(option));
        when(options.findById(12L)).thenReturn(Optional.of(option));
        var old = new ServiceTypeDocumentRequirement(); old.setOptionId(12L); old.setDocumentTypeId(2L);
        var other = new ServiceTypeDocumentRequirement(); other.setOptionId(13L); other.setDocumentTypeId(2L);
        var common = new ServiceTypeDocumentRequirement(); common.setDocumentTypeId(4L);
        var state = new ArrayList<>(List.of(old,other,common));
        when(requirements.findAllByServiceProviderTypeId(1L)).thenAnswer(i -> List.copyOf(state));
        doAnswer(i -> { state.removeAll(i.getArgument(0)); return null; }).when(requirements).deleteAll(anyList());
        when(requirements.save(any())).thenAnswer(i -> { ServiceTypeDocumentRequirement r=i.getArgument(0);state.add(r);return r; });
        var type = new DocumentType(); type.setDocumentTypeId(3L); type.setName("Licence");type.setStatus("ACTIVE");
        when(types.findById(3L)).thenReturn(Optional.of(type));
        var registration = new ProviderServiceOption();registration.setAssignmentId(10L);
        when(registrations.findAllByOptionIdIn(List.of(12L))).thenReturn(List.of(registration));
        var assignment = new ServiceProviderTypeAssignment();assignment.setServiceProviderId(15L);
        when(assignments.findById(10L)).thenReturn(Optional.of(assignment));
        var result=service.replace(12L,new OptionDocumentRequirementsRequest(List.of(
                new OptionDocumentRequirementsRequest.Item(3L,true,true))));
        assertThat(result).extracting(r -> r.documentTypeId()).containsExactly(3L);
        assertThat(state).contains(other,common).doesNotContain(old);
        verify(approvals).refreshProviderApprovals(15L);
    }
}
