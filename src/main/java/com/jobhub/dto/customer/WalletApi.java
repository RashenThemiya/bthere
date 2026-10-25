package com.jobhub.dto.customer;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class WalletApi {
    private WalletApi() {}
    public record Balance(Long walletId, String currencyCode, BigDecimal available,
                          BigDecimal reserved, BigDecimal pending, String status, String fundingMode) {}
    public record Entry(Long entryId, Long operationId, String currencyCode, String bucket,
                        BigDecimal delta, BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDateTime createdAt,
                        String type,Long jobId,BigDecimal grossAmount,BigDecimal platformFeePercentage,
                        BigDecimal platformFee,BigDecimal providerEarnings,String fundingMode,String paymentReference) {}
    public record Capabilities(boolean walletLedger, boolean bookingReservations, boolean cardCheckout,
                               boolean topups, boolean withdrawals, boolean cardLinking, boolean bankLinking) {}
    public record Amount(@NotNull @DecimalMin("0.0001") BigDecimal amount, Long methodId) {
        public Amount(BigDecimal amount) { this(amount,null); }
    }
    public record Operation(Long operationId, String type, Long jobId, String currencyCode,
                            BigDecimal amount, String status,BigDecimal platformFeePercentage,
                            BigDecimal platformFee,BigDecimal providerEarnings,String fundingMode,String paymentReference) {
        public Operation(Long operationId,String type,Long jobId,String currencyCode,BigDecimal amount,String status){
            this(operationId,type,jobId,currencyCode,amount,status,null,null,null,null,null);
        }
    }
    public record Reconciliation(Long walletId, boolean balanced, BigDecimal available,
                                 BigDecimal reserved, BigDecimal pending) {}
}
