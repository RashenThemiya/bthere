package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.entity.market.RefundPolicyTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundCalculatorTest {
    RefundPolicy policy;
    Job job;

    @BeforeEach void setup() {
        policy = new RefundPolicy(); policy.setPolicyId(3L); policy.setVersion(2);
        policy.replaceTiers(List.of(tier("Early", 1440, "100", "0"), tier("Two hours", 120, "50", "200"), tier("Late", 0, "0", "0")));
        job = new Job(); job.setJobId(70L); job.setCurrencyCode("LKR");
        job.setExpectedAmount(new BigDecimal("10000.00")); job.setPlatformFee(new BigDecimal("1500.00"));
        job.setProviderEarnings(new BigDecimal("8500.00"));
    }

    private RefundPolicyTier tier(String name, int minutes, String percentage, String fee) {
        var tier = new RefundPolicyTier(); tier.setName(name); tier.setMinimumMinutesBeforeStart(minutes);
        tier.setRefundPercentage(new BigDecimal(percentage)); tier.setCancellationFee(new BigDecimal(fee)); tier.setPriority(minutes);
        return tier;
    }

    @Test void earlyCancellationRefundsEverythingWhenPlatformFeeIsRefundable() {
        policy.setPlatformFeeRefundable(true);
        var quote = RefundCalculator.customer(policy, job, 2000);
        assertThat(quote.refundTierName()).isEqualTo("Early");
        assertThat(quote.refundAmount()).isEqualByComparingTo("10000");
        assertThat(quote.retainedAmount()).isEqualByComparingTo("0");
        assertThat(quote.refundPolicyVersion()).isEqualTo(2);
    }

    @Test void middleTierAppliesPercentageFeeAndPolicyProviderShare() {
        policy.setPlatformFeeRefundable(true); policy.setRetainedProviderSharePercentage(new BigDecimal("70"));
        var quote = RefundCalculator.customer(policy, job, 300);
        // 50% of 10,000 minus 200 fee
        assertThat(quote.refundAmount()).isEqualByComparingTo("4800");
        assertThat(quote.retainedAmount()).isEqualByComparingTo("5200");
        assertThat(quote.providerAmount()).isEqualByComparingTo("3640");
        assertThat(quote.platformAmount()).isEqualByComparingTo("1560");
    }

    @Test void nonRefundablePlatformFeeAndDefaultSplitFollowBookingRatio() {
        var quote = RefundCalculator.customer(policy, job, 300);
        // 50% of (10,000 - 1,500) minus 200
        assertThat(quote.refundAmount()).isEqualByComparingTo("4050");
        assertThat(quote.providerAmount()).isEqualByComparingTo("5057.50");
        assertThat(quote.platformAmount()).isEqualByComparingTo("892.50");
        assertThat(quote.refundAmount().add(quote.providerAmount()).add(quote.platformAmount())).isEqualByComparingTo("10000");
    }

    @Test void lateOrAlreadyStartedCancellationKeepsEverythingAndFeeNeverMakesRefundNegative() {
        var late = RefundCalculator.customer(policy, job, -15);
        assertThat(late.refundTierName()).isEqualTo("Late");
        assertThat(late.refundAmount()).isEqualByComparingTo("0");
        policy.replaceTiers(List.of(tier("Tiny", 0, "1", "500")));
        assertThat(RefundCalculator.customer(policy, job, 10).refundAmount()).isEqualByComparingTo("0");
    }

    @Test void providerCancellationRefundsInFull() {
        var quote = RefundCalculator.provider(policy, job, -5);
        assertThat(quote.cancelledBy()).isEqualTo("PROVIDER");
        assertThat(quote.refundAmount()).isEqualByComparingTo("10000");
        assertThat(quote.providerAmount()).isEqualByComparingTo("0");
        assertThat(quote.platformAmount()).isEqualByComparingTo("0");
        assertThat(RefundCalculator.provider(null, job, 30).refundPolicyId()).isNull();
    }
}
