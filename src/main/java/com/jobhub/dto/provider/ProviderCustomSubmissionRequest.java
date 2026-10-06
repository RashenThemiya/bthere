package com.jobhub.dto.provider;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProviderCustomSubmissionRequest(
        @NotNull List<@Valid Entry> submissions
) {
    public record Entry(@NotNull Long fieldId, @NotNull JsonNode value) {}
}
