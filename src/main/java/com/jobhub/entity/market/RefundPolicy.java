package com.jobhub.entity.market;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "refund_policy", uniqueConstraints = @UniqueConstraint(
        name = "uk_refund_policy_market_version", columnNames = {"market_id", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class RefundPolicy extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "offering_id")
    private Long offeringId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal noProviderRefundPercentage;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal providerCancellationRefundPercentage;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal providerNoShowRefundPercentage;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceFailureRefundPercentage;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal customerNoShowRefundPercentage;

    @Column(nullable = false)
    private boolean platformFeeRefundable;

    @Column(nullable = false)
    private boolean processingFeeRefundable;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant effectiveFrom;

    private Instant effectiveTo;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC, minimumMinutesBeforeStart DESC")
    private List<RefundPolicyTier> tiers = new ArrayList<>();

    public void replaceTiers(List<RefundPolicyTier> replacement) {
        tiers.clear();
        replacement.forEach(tier -> {
            tier.setPolicy(this);
            tiers.add(tier);
        });
    }
}
