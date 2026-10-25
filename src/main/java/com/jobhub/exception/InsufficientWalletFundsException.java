package com.jobhub.exception;
import java.math.BigDecimal;
import lombok.Getter;
@Getter
public class InsufficientWalletFundsException extends ConflictException {
    private final String currencyCode;
    private final BigDecimal requiredAmount,availableAmount,shortfall;
    public InsufficientWalletFundsException(String currencyCode,BigDecimal requiredAmount,BigDecimal availableAmount) {
        super("Insufficient available wallet balance");this.currencyCode=currencyCode;
        this.requiredAmount=requiredAmount;this.availableAmount=availableAmount;this.shortfall=requiredAmount.subtract(availableAmount);
    }
}
