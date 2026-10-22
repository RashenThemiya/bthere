package com.jobhub.dto.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record OptionDocumentRequirementsRequest(
        @NotNull List<@NotNull @Valid Item> documents) {
    public record Item(@NotNull @Positive Long documentTypeId, boolean required, boolean requiresApproval) {}
}
