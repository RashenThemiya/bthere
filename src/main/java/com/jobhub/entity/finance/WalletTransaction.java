package com.jobhub.entity.finance;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "wallet_transaction")
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletTransactionId;
    private Long walletId;
    private Long jobId;
    private String transactionType;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String referenceType;
    private Long referenceId;
    private String description;
}

