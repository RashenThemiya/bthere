package com.jobhub.dto.admin;

import com.jobhub.dto.provider.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AdminProviderRegistrationApi {
    private AdminProviderRegistrationApi() {}
    public enum Source { WHATSAPP, PHONE, IN_PERSON, EMAIL, ADMIN_ASSISTED }
    public record Registration(@NotNull @Valid ProviderServiceRegistrationRequest registration,
                               @NotNull Source source, @Size(max = 1000) String note) {}
    public record Document(@NotNull @Valid ProviderDocumentRequest document,
                           @NotNull Source source, @Size(max = 1000) String note) {}
    public record Context(Long providerId, List<ProviderTypeAssignmentResponse> assignments,
                          ProviderOnboardingResponse completion, List<ProviderDocumentResponse> documents) {}
}
