package com.jobhub.dto.admin;

import com.jobhub.dto.job.BookingResponse;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;

/** Explicit read models: never serialize checkout payloads, start codes or merchant signatures. */
public final class BookingMonitorApi {
    private BookingMonitorApi() {}
    public record RequestFilter(@Positive Long requestId,@Positive Long jobId,@Positive Long customerUserId,
        @Positive Long marketId,@Positive Long optionId,@Positive Long providerId,
        @Size(max=60) String status,@Size(max=60) String paymentStatus,@Size(max=60) String settlementStatus,
        @Size(max=30) String fundingMode,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {}
    public record BookingFilter(@Positive Long jobId,@Positive Long customerId,@Positive Long providerId,
        @Positive Long marketId,@Positive Long serviceTypeId,@Positive Long optionId,
        @Size(max=60) String status,@Size(max=60) String paymentStatus,@Size(max=30) String paymentMethod,
        @Size(max=30) String schedulingModel,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {}
    public record OfferFilter(@Positive Long requestId,@Positive Long providerId,@Size(max=60) String status,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {}
    public record PaymentFilter(@Positive Long requestId,@Size(max=100) String paymentId,
        @Size(max=3) String statusCode,@Size(max=30) String fundingMode,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant receivedFrom,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant receivedTo) {}
    public record TransactionFilter(@Positive Long jobId,@Positive Long ownerUserId,@Size(max=60) String type,
        @Size(max=60) String status,@Size(max=30) String fundingMode,@Size(max=100) String paymentReference,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {}
    public record EventFilter(@Positive Long requestId,@Positive Long userId,@Size(max=60) String eventType,
        @Size(max=60) String deliveryStatus,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant createdTo) {}
    public record Summary(Map<String,Long> requestsByStatus,Map<String,Long> bookingsByStatus,
        Map<String,Long> requestPaymentsByStatus,Map<String,Long> requestSettlementsByStatus) {}
    public record RequestRow(Long requestId,Long customerUserId,Long marketId,Long optionId,String selectionMode,
        String status,Long jobId,String paymentStatus,BigDecimal expectedAmount,String currencyCode,
        BigDecimal platformFeePercentage,BigDecimal platformFee,BigDecimal providerEarnings,
        String fundingMode,String paymentOrderId,String gatewayPaymentId,String settlementStatus,Long settlementOperationId,
        Instant expiresAt,Instant confirmationExpiresAt,Instant paymentStartedAt,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record RequestDetail(RequestRow request,BookingRow booking) {}
    public record BookingRow(Long jobId,Long requestId,Long customerId,Long providerId,Long marketId,Long serviceTypeId,
        Long optionId,String status,String paymentMethod,String paymentStatus,String schedulingModel,
        String deliveryMode,String fulfillmentModel,String currencyCode,BigDecimal expectedAmount,
        BigDecimal totalAmount,BigDecimal platformFeePercentage,BigDecimal platformFee,BigDecimal providerEarnings,
        LocalDateTime startDatetime,LocalDateTime expectedEndDatetime,LocalDateTime actualStartDatetime,
        LocalDateTime actualEndDatetime,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record BookingDetail(BookingRow booking,RequestRow request,BookingResponse details) {}
    public record OfferRow(Long offerId,Long requestId,Long providerId,Long providerLocationId,String status,
        Instant expiresAt,LocalDateTime createdAt) {}
    public record PaymentRow(Long noticeId,Long requestId,String paymentId,String statusCode,String status,
        Instant receivedAt,boolean primaryPayment,String paymentOrderId,String requestPaymentStatus,
        String currencyCode,BigDecimal bookingAmount,String fundingMode,Long jobId,
        String settlementStatus,Long settlementOperationId) {}
    public record TransactionRow(Long operationId,String type,Long ownerUserId,Long actorUserId,Long jobId,
        String currencyCode,BigDecimal amount,String status,BigDecimal platformFeePercentage,
        BigDecimal platformFee,BigDecimal providerEarnings,String fundingMode,String paymentReference,LocalDateTime createdAt) {}
    public record LedgerRow(Long entryId,Long operationId,Long walletId,String accountKey,String bucket,
        String currencyCode,BigDecimal delta,BigDecimal balanceBefore,BigDecimal balanceAfter,LocalDateTime createdAt) {}
    public record EventRow(Long eventId,Long requestId,Long offerId,Long userId,String eventType,
        Instant createdAt,Instant expiresAt,String deliveryStatus,int deliveryAttempts,Instant nextDeliveryAt) {}
}
