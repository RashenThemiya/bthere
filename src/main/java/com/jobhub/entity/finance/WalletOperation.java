package com.jobhub.entity.finance;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "wallet_operation", uniqueConstraints = @UniqueConstraint(columnNames = "operation_key"))
@Getter @Setter @NoArgsConstructor
public class WalletOperation extends CreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long operationId;
    @Column(nullable = false, length = 160) private String operationKey;
    private String type;
    private Long ownerUserId;
    private Long jobId;
    private String currencyCode;
    @Column(precision = 19, scale = 4) private BigDecimal amount;
    private String status;
    private Long actorUserId;
    @Column(precision=5,scale=2) private BigDecimal platformFeePercentage;
    @Column(precision=19,scale=4) private BigDecimal platformFee;
    @Column(precision=19,scale=4) private BigDecimal providerEarnings;
    private String fundingMode;
    @Column(length=100) private String paymentReference;
}
