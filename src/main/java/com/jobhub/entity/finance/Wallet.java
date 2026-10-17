package com.jobhub.entity.finance;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(
        name = "wallet",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_user_currency",
                columnNames = {"user_id", "currency_code"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Wallet extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;
    private Long userId;
    private String currencyCode;
    @Column(precision = 19, scale = 4) private BigDecimal availableBalance;
    @Column(precision = 19, scale = 4) private BigDecimal pendingBalance;
    @Column(precision = 19, scale = 4) private BigDecimal reservedBalance;
    private String status;
    private String fundingMode;
    @Version private Long version;
}

