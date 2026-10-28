package com.jobhub.dto.customer;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class GatewayApi {
    private GatewayApi() {}
    public record Settings(WalletApi.Capabilities capabilities) {}
    public record Method(Long methodId,String type,String displayLabel,String brand,String status) {}
    public record Intent(Long intentId,String purpose,String currencyCode,
                         BigDecimal amount,Long jobId,Long methodId,String status) {}
    public record Event(@NotBlank @Pattern(regexp="SUCCESS|FAILURE|PENDING|REFUND") String outcome) {}
}

