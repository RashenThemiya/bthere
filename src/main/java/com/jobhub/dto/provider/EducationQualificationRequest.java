package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EducationQualificationRequest(
        @NotBlank @Size(max = 200) String qualificationName,
        @NotBlank @Size(max = 200) String instituteName,
        @Size(max = 200) String fieldOfStudy,
        LocalDate startDate,
        LocalDate completionDate,
        @Size(max = 100) String result,
        @Size(max = 2048)
        @Pattern(regexp = "^(?!.*\\.\\.)providers/[A-Za-z0-9/_-]+\\.[A-Za-z0-9]+$")
        String documentKey
) {}
