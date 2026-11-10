package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.Quote;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.entity.market.RefundPolicyTier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

/** Pure refund arithmetic for cancelled paid bookings. Amounts are LKR with 2 decimals; rounding favours no one twice. */
public final class RefundCalculator {
    public static final String CUSTOMER = "CUSTOMER", PROVIDER = "PROVIDER", WALLET = "WALLET";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private RefundCalculator() {}

    /**
     * Customer cancellation: the tier with the largest minimumMinutesBeforeStart that is still reached gives the
     * refund percentage and fee. The platform fee is excluded from the refundable base unless the policy refunds it.
     * The kept amount is split between provider and platform.
     */
    public static Quote customer(RefundPolicy policy, Job job, long minutesBeforeStart) {
        BigDecimal paid = money(job.getExpectedAmount());
        BigDecimal platformFee = job.getPlatformFee() == null ? BigDecimal.ZERO : money(job.getPlatformFee()).min(paid);
        RefundPolicyTier tier = policy.getTiers().stream()
                .filter(t -> t.getMinimumMinutesBeforeStart() <= Math.max(0, minutesBeforeStart))
                .max(Comparator.comparing(RefundPolicyTier::getMinimumMinutesBeforeStart))
                .orElse(null);
        BigDecimal percentage = tier == null ? BigDecimal.ZERO : tier.getRefundPercentage();
        BigDecimal fee = tier == null || tier.getCancellationFee() == null ? BigDecimal.ZERO : money(tier.getCancellationFee());
        BigDecimal base = policy.isPlatformFeeRefundable() ? paid : paid.subtract(platformFee);
        BigDecimal refund = percent(base, percentage).subtract(fee).max(BigDecimal.ZERO).min(paid);
        BigDecimal retained = paid.subtract(refund);
        BigDecimal provider = policy.getRetainedProviderSharePercentage() != null
                ? percent(retained, policy.getRetainedProviderSharePercentage())
                : providerRatio(job, paid, retained);
        provider = provider.min(retained);
        return new Quote(job.getJobId(), CUSTOMER, job.getCurrencyCode(), paid, refund, percentage, fee, retained,
                provider, retained.subtract(provider), policy.isPlatformFeeRefundable(), WALLET, minutesBeforeStart,
                policy.getPolicyId(), policy.getVersion(), tier == null ? null : tier.getName());
    }

    /** Provider cancellation always refunds the customer in full; the provider and platform keep nothing. */
    public static Quote provider(RefundPolicy policy, Job job, long minutesBeforeStart) {
        BigDecimal paid = money(job.getExpectedAmount());
        return new Quote(job.getJobId(), PROVIDER, job.getCurrencyCode(), paid, paid, HUNDRED.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), true, WALLET, minutesBeforeStart,
                policy == null ? null : policy.getPolicyId(), policy == null ? null : policy.getVersion(), null);
    }

    private static BigDecimal providerRatio(Job job, BigDecimal paid, BigDecimal retained) {
        if (paid.signum() == 0 || job.getProviderEarnings() == null) return BigDecimal.ZERO.setScale(2);
        return retained.multiply(job.getProviderEarnings()).divide(paid, 2, RoundingMode.DOWN);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(HUNDRED, 2, RoundingMode.DOWN);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
