package com.jobhub.entity.market;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "refund_policy_tier", uniqueConstraints = @UniqueConstraint(
        name = "uk_refund_policy_tier_threshold",
        columnNames = {"policy_id", "minimum_minutes_before_start"}))
@Getter
@Setter
@NoArgsConstructor
public class RefundPolicyTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tierId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private RefundPolicy policy;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer minimumMinutesBeforeStart;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercentage;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cancellationFee;

    @Column(nullable = false)
    private Integer priority;
}
