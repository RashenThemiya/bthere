package com.jobhub.dto.customer;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class CustomerRequirementsApi {
    private CustomerRequirementsApi() {}
    public record Target(@NotBlank String scope, Long serviceTypeId, Long optionId) {}
    public record Definition(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,99}") String code,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 1000) String description,
            @NotBlank String fieldType,
            boolean required,
            @NotBlank String verificationMode,
            boolean blocksBooking,
            @NotEmpty List<@NotNull @Valid Target> targets,
            @NotNull List<@NotBlank String> evidenceCodes,
            @NotNull List<@NotBlank String> choices,
            @Min(1) @Max(20) Integer minimumFiles,
            @Min(1) @Max(20) Integer maximumFiles,
            @Min(1) @Max(100) Integer maximumFileSizeMb,
            @NotNull List<@NotBlank String> allowedFileTypes,
            @Min(0) Integer displayOrder
    ) {}
    public record Requirement(Long requirementId, int version, String status, Definition definition) {}
    public record Entry(@NotNull Long requirementId, @NotNull JsonNode value, LocalDate expiresOn) {}
    public record Submit(@NotNull @Size(max = 100) List<@NotNull @Valid Entry> submissions) {}
    public record Submission(Long submissionId, Long customerId, Long requirementId, String code,
                             JsonNode value, String status, String storedStatus, LocalDate expiresOn,
                             Long reviewedBy, LocalDateTime reviewedAt, String rejectionReason,
                             int answerVersion, int definitionVersion) {}
    public record FormItem(Requirement requirement, Submission submission) {}
    public record Review(@NotBlank String status, @Size(max = 1000) String rejectionReason,
                         @NotNull @Min(1) Integer answerVersion) {}
    public record Status(@NotBlank String status) {}
    public record AgeRule(boolean enabled, @Min(0) @Max(150) Integer minimumAge,
                          String dateOfBirthCode, boolean requiresVerifiedDateOfBirth,
                          List<@NotBlank String> requiredCodes) {
        public AgeRule {
            requiredCodes = requiredCodes == null ? List.of() : List.copyOf(requiredCodes);
        }
        public AgeRule(boolean enabled, Integer minimumAge, String dateOfBirthCode, boolean verified) {
            this(enabled, minimumAge, dateOfBirthCode, verified, List.of());
        }
    }
    public record Rule(Long ruleId, Target target, AgeRule configuration) {}
    public record Reason(String code, String requirementCode, String message) {}
    public record Eligibility(boolean eligible, List<Reason> reasons) {}
}
