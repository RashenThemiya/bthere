package com.jobhub.dto.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProviderTypesRequest(
        @NotEmpty(message = "At least one provider type is required")
        @Size(max = 50, message = "A provider cannot select more than 50 provider types")
        List<@Valid ProviderTypeAssignmentRequest> assignments
) {
}
