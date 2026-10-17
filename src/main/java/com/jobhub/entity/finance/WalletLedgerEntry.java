package com.jobhub.entity.finance;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

@Entity @Immutable
@Table(name = "wallet_ledger_entry", indexes = @Index(name="idx_wallet_ledger_history", columnList="wallet_id,wallet_ledger_entry_id"))
@Getter @Setter @NoArgsConstructor
public class WalletLedgerEntry extends CreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long walletLedgerEntryId;
    @Column(nullable = false) private Long operationId;
    private Long walletId;
    @Column(nullable = false) private String accountKey;
    @Column(nullable = false) private String bucket;
    private String currencyCode;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal delta;
    @Column(precision = 19, scale = 4) private BigDecimal balanceBefore;
    @Column(precision = 19, scale = 4) private BigDecimal balanceAfter;
}
