package com.jobhub.dto.provider;

import jakarta.validation.constraints.*;
import java.util.List;

public record DocumentPartDefinition(
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,49}") String code,
        @NotBlank @Size(max=150) String label,
        boolean required,
        @Min(1) @Max(100) int maximumFiles,
        @Min(1) @Max(100) int maximumFileSizeMb,
        @NotEmpty List<@NotBlank String> allowedFileTypes
) {}
