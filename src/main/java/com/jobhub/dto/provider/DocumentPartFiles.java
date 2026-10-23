package com.jobhub.dto.provider;

import jakarta.validation.constraints.*;
import java.util.List;

public record DocumentPartFiles(
        @NotBlank String code,
        @NotEmpty @Size(max=100) List<@NotBlank @Size(max=2048) String> documentKeys
) {}
