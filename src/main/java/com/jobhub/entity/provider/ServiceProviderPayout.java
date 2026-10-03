package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_provider_payout")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderPayout extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payoutId;
    private Long serviceProviderId;
    private Long walletId;
    private Long bankAccountId;
    private BigDecimal amount;
    private String status;
    private String transactionReference;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
}

