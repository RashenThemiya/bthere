package com.jobhub.dto.admin;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public final class ProviderApprovalApi {
 private ProviderApprovalApi() {}
 public record Requirement(String type,Long id,Long definitionId,String name,boolean required,
     boolean requiresApproval,String verificationStatus,boolean missing,boolean expired,
     boolean reviewable,String documentKey,String rejectionReason,LocalDate expiryDate,
     Long sourceAssignmentId,JsonNode details) {}
 public record ServiceDetail(Long assignmentId,Long serviceTypeId,String serviceName,
     String verificationStatus,String providerStatus,String adminStatus,boolean emergencyOverride,
     Map<String,Boolean> checks,int pendingCount,int missingCount,
     List<Requirement> documents,List<Requirement> skills,List<Requirement> certificates,
     List<Requirement> education,List<Requirement> dynamicRequirements,List<Requirement> serviceOptions) {
  public List<Requirement> items() { return java.util.stream.Stream.of(documents,skills,certificates,education,dynamicRequirements,serviceOptions).flatMap(List::stream).toList(); }
 }
 public record Detail(Long providerId,String providerName,int pendingCount,int missingCount,List<ServiceDetail> services) {}
 public record ServiceSummary(Long assignmentId,String serviceName,String verificationStatus,int pendingCount,int missingCount) {}
 public record Summary(Long providerId,String providerName,int pendingCount,int missingCount,List<ServiceSummary> services) {}
 public record Review(@NotBlank @Pattern(regexp="DOCUMENT|SKILL|CERTIFICATE|EDUCATION|DYNAMIC_REQUIREMENT|SERVICE_OPTION") String type,
     @NotNull @Positive Long id,@NotBlank @Pattern(regexp="APPROVED|REJECTED") String status,
     @Size(max=500) String rejectionReason) {}
 public record BulkRequest(@NotNull @Positive Long assignmentId,@NotEmpty @Size(max=100) List<@NotNull @Valid Review> reviews) {}
 public record BulkResponse(boolean atomic,int reviewedCount,Detail provider) {}
}
