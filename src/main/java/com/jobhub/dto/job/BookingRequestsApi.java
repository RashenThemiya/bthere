package com.jobhub.dto.job;

import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class BookingRequestsApi {
    private BookingRequestsApi() {}
    public record Create(@NotNull Long marketId, @NotNull @Valid CreateBookingRequest booking,
                         @Valid Point searchLocation,
                         @DecimalMin("0.1") @DecimalMax("500") BigDecimal radiusKm,
                         List<@NotNull @Pattern(regexp="SINHALA|ENGLISH|TAMIL") String> languages,
                         List<@NotNull Long> requiredSkillIds,
                         @Size(max=50) List<@NotNull @Valid ProviderFilter> providerFilters) {}
    /** Same shape as Create so the booking form can send its draft; providerId must be omitted. */
    public record Estimate(@NotNull Long marketId, @NotNull @Valid CreateBookingRequest booking,
                           @Valid Point searchLocation) {}
    public record Policy(boolean enabled, @Min(1) @Max(43200) int minimumNoticeMinutes,
                         @Min(15) @Max(600) int offerTimeoutSeconds,
                         @Min(30) @Max(900) int confirmationTimeoutSeconds,
                         @Min(60) @Max(3600) int matchingTimeoutSeconds,
                         @NotNull @DecimalMin("0.1") @DecimalMax("500") BigDecimal maximumRadiusKm,
                         @Min(1) @Max(100) Integer offerGroupSize) {
        public Policy(boolean enabled,int minimumNoticeMinutes,int offerTimeoutSeconds,
                      int confirmationTimeoutSeconds,int matchingTimeoutSeconds,BigDecimal maximumRadiusKm) {
            this(enabled,minimumNoticeMinutes,offerTimeoutSeconds,confirmationTimeoutSeconds,matchingTimeoutSeconds,maximumRadiusKm,null);
        }
    }
    public record View(Long requestId, String status, String selectionMode, Long offerId,
                       Long providerId, Instant offerExpiresAt, Instant confirmationExpiresAt,
                       Long jobId, String paymentStatus, BigDecimal expectedAmount, String currencyCode,
                       BigDecimal platformFeePercentage,BigDecimal platformFee,BigDecimal providerEarnings,
                       String settlementStatus,Long settlementOperationId,String paymentFundingMode,String paymentMethod) {}
    public record Offer(Long offerId, Long requestId, String status, Instant expiresAt,
                        Long serviceTypeId, Long optionId, String deliveryMode,
                        LocalDateTime startDatetime, LocalDateTime expectedEndDatetime,
                        LocalDateTime requestedPickupTime, Long providerLocationId,
                        BigDecimal expectedAmount, String currencyCode,
                        CreateBookingRequest.RoutePoint pickup, CreateBookingRequest.RoutePoint destination,
                        CreateBookingRequest.MeetingPoint meetingPoint,
                        BigDecimal platformFeePercentage,BigDecimal platformFee,BigDecimal providerEarnings) {}
    public record Confirm(@NotBlank @Pattern(regexp="VISA|MASTER") String cardMethod,
                          @NotBlank @Size(max=100) String firstName,
                          @NotBlank @Size(max=100) String lastName,
                          @NotBlank @Email @Size(max=255) String email,
                          @NotBlank @Size(max=30) String phone,
                          @NotBlank @Size(max=255) String address,
                          @NotBlank @Size(max=100) String city,
                          @NotBlank @Size(max=100) String country) {}
    public record Checkout(String actionUrl, Map<String,String> fields, String status) {}
    public record MockCard(@NotNull @Positive Long methodId) {}
    public record PaymentDeadline(@NotNull Instant confirmationExpiresAt) {}
    public record PaymentWindow(Long requestId,String status,String paymentStatus,Instant confirmationExpiresAt,
                                Instant paymentDeadlineLimit,boolean canPay,boolean canCancel,boolean canExtend) {}
    public record Code(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Codes(String purpose, String code, Instant expiresAt) {}
    public record Transition(boolean successful, String status) {}
}
