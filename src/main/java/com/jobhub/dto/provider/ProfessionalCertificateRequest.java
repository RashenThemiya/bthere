package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProfessionalCertificateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String issuingOrganization,
        @Size(max = 150) String certificateNumber,
        LocalDate issuedDate,
        LocalDate expiryDate,
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^(?!.*\\.\\.)providers/[A-Za-z0-9/_-]+\\.[A-Za-z0-9]+$")
        String documentKey
) {}
