package com.jobhub.repository.market;

import com.jobhub.entity.market.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
    List<RefundPolicy> findAllByMarketIdOrderByVersionDesc(Long marketId);
    List<RefundPolicy> findAllByMarketIdAndStatus(Long marketId, String status);
    Optional<RefundPolicy> findByPolicyIdAndMarketId(Long policyId, Long marketId);
    Optional<RefundPolicy> findTopByMarketIdOrderByVersionDesc(Long marketId);
}
