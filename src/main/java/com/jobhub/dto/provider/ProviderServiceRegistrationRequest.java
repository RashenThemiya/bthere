package com.jobhub.dto.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ProviderServiceRegistrationRequest(
        @NotNull @Valid ProviderTypeAssignmentRequest service,
        @NotNull @Valid UpdateProviderOptionsRequest options,
        @NotNull @Valid UpdateProviderLanguagesRequest languages,
        @NotNull @Valid UpdateProviderSkillsRequest skills,
        @NotNull List<@NotNull @Valid ProviderDocumentRequest> documents,
        @NotNull List<@NotNull @Valid ProfessionalCertificateRequest> certificates,
        @NotNull List<@NotNull @Valid EducationQualificationRequest> education,
        @Valid ProviderScheduleRequest availability,
        @NotNull List<@NotNull @Valid ProviderUnavailableDateRequest> unavailableDates,
        @NotNull List<@NotNull @Valid ServiceAreaGroup> serviceAreas,
        @NotNull @Valid ProviderCustomSubmissionRequest requirements
) {
    public record ServiceAreaGroup(
            @NotNull Long optionId,
            @NotBlank String deliveryMode,
            @NotEmpty List<@NotNull @Valid ProviderServiceAreaRequest> locations
    ) {}
}
