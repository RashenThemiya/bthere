package com.jobhub.service.market;

import com.jobhub.dto.market.*;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.MarketServiceOffering;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.entity.market.RefundPolicyTier;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefundPolicyService {
    private final RefundPolicyRepository policyRepository;
    private final MarketRepository marketRepository;
    private final MarketServiceOfferingRepository offeringRepository;
    private final AuditService auditService;

    @Transactional
    public RefundPolicyResponse create(Long actorId, Long marketId, RefundPolicyRequest request) {
        Market market = requireMarket(marketId);
        validateRequest(marketId, request);

        RefundPolicy policy = new RefundPolicy();
        policy.setMarketId(marketId);
        policy.setVersion(policyRepository.findTopByMarketIdOrderByVersionDesc(marketId)
                .map(item -> item.getVersion() + 1).orElse(1));
        policy.setStatus("DRAFT");
        apply(policy, market, request);
        policy = policyRepository.save(policy);
        RefundPolicyResponse response = response(policy);
        auditService.record(actorId, "REFUND_POLICY_CREATED", "REFUND_POLICY",
                policy.getPolicyId(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> list(Long marketId) {
        requireMarket(marketId);
        return policyRepository.findAllByMarketIdOrderByVersionDesc(marketId).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public RefundPolicyResponse get(Long marketId, Long policyId) {
        return response(requirePolicy(marketId, policyId));
    }

    @Transactional
    public RefundPolicyResponse update(Long actorId, Long marketId, Long policyId,
                                       RefundPolicyRequest request) {
        Market market = requireMarket(marketId);
        RefundPolicy policy = requirePolicy(marketId, policyId);
        if (!"DRAFT".equals(policy.getStatus())) {
            throw new ConflictException("Only a DRAFT refund policy can be edited");
        }
        validateRequest(marketId, request);
        RefundPolicyResponse old = response(policy);

        // Flush orphan removals before inserting replacement tiers. Without this
        // explicit boundary Hibernate may insert a replacement threshold (for
        // example, 0 minutes) before deleting the old row with the same
        // (policy_id, minimum_minutes_before_start) unique key.
        policy.replaceTiers(List.of());
        policyRepository.flush();

        apply(policy, market, request);
        policy = policyRepository.save(policy);
        RefundPolicyResponse updated = response(policy);
        auditService.record(actorId, "REFUND_POLICY_UPDATED", "REFUND_POLICY",
                policyId, old, updated);
        return updated;
    }

    @Transactional
    public RefundPolicyResponse activate(Long actorId, Long marketId, Long policyId) {
        RefundPolicy policy = requirePolicy(marketId, policyId);
        if (!"DRAFT".equals(policy.getStatus())) {
            throw new ConflictException("Only a DRAFT refund policy can be activated");
        }
        Long offeringId = policy.getOfferingId();
        Instant effectiveFrom = policy.getEffectiveFrom();

        List<RefundPolicy> sameScope = policyRepository
                .findAllByMarketIdAndStatus(marketId, "ACTIVE").stream()
                .filter(item -> Objects.equals(item.getOfferingId(), offeringId))
                .toList();
        if (sameScope.stream().anyMatch(item ->
                !item.getEffectiveFrom().isBefore(effectiveFrom))) {
            throw new ConflictException(
                    "An active policy for this scope starts at or after effectiveFrom");
        }
        sameScope.stream()
                .filter(item -> item.getEffectiveTo() == null
                        || item.getEffectiveTo().isAfter(effectiveFrom))
                .forEach(item -> item.setEffectiveTo(effectiveFrom));

        policy.setStatus("ACTIVE");
        policy = policyRepository.save(policy);
        RefundPolicyResponse result = response(policy);
        auditService.record(actorId, "REFUND_POLICY_ACTIVATED", "REFUND_POLICY",
                policyId, null, result);
        return result;
    }

    @Transactional
    public RefundPolicyResponse deactivate(Long actorId, Long marketId, Long policyId) {
        RefundPolicy policy = requirePolicy(marketId, policyId);
        if (!"ACTIVE".equals(policy.getStatus())) {
            throw new ConflictException("Only an ACTIVE refund policy can be deactivated");
        }
        Instant now = Instant.now();
        if (policy.getEffectiveTo() == null || policy.getEffectiveTo().isAfter(now)) {
            policy.setEffectiveTo(now);
        }
        policy.setStatus("INACTIVE");
        policy = policyRepository.save(policy);
        RefundPolicyResponse result = response(policy);
        auditService.record(actorId, "REFUND_POLICY_DEACTIVATED", "REFUND_POLICY",
                policyId, null, result);
        return result;
    }

    @Transactional(readOnly = true)
    public RefundPolicyResponse resolve(Long marketId, Long offeringId) {
        requireMarket(marketId);
        if (offeringId != null) {
            requireOfferingInMarket(marketId, offeringId);
        }
        Instant now = Instant.now();
        List<RefundPolicy> effective = policyRepository
                .findAllByMarketIdAndStatus(marketId, "ACTIVE").stream()
                .filter(item -> !item.getEffectiveFrom().isAfter(now))
                .filter(item -> item.getEffectiveTo() == null || item.getEffectiveTo().isAfter(now))
                .toList();

        if (offeringId != null) {
            RefundPolicy override = latest(effective.stream()
                    .filter(item -> offeringId.equals(item.getOfferingId())).toList());
            if (override != null) return response(override);
        }
        RefundPolicy marketDefault = latest(effective.stream()
                .filter(item -> item.getOfferingId() == null).toList());
        if (marketDefault == null) {
            throw new ResourceNotFoundException("No active refund policy was found");
        }
        return response(marketDefault);
    }

    private RefundPolicy latest(List<RefundPolicy> policies) {
        return policies.stream().max(Comparator.comparing(RefundPolicy::getEffectiveFrom)
                .thenComparing(RefundPolicy::getVersion)).orElse(null);
    }

    private void validateRequest(Long marketId, RefundPolicyRequest request) {
        if (request.effectiveTo() != null
                && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
        if (request.offeringId() != null) {
            requireOfferingInMarket(marketId, request.offeringId());
        }
        Set<Integer> thresholds = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        boolean hasCatchAll = false;
        for (RefundPolicyTierRequest tier : request.tiers()) {
            if (!thresholds.add(tier.minimumMinutesBeforeStart())) {
                throw new IllegalArgumentException(
                        "Cancellation tier thresholds must be unique");
            }
            if (!priorities.add(tier.priority())) {
                throw new IllegalArgumentException(
                        "Cancellation tier priorities must be unique");
            }
            hasCatchAll |= tier.minimumMinutesBeforeStart() == 0;
        }
        if (!hasCatchAll) {
            throw new IllegalArgumentException(
                    "A cancellation tier with minimumMinutesBeforeStart 0 is required");
        }
    }

    private void apply(RefundPolicy policy, Market market, RefundPolicyRequest request) {
        policy.setOfferingId(request.offeringId());
        policy.setName(request.name().trim());
        policy.setDescription(optional(request.description()));
        policy.setCurrencyCode(market.getDefaultCurrency());
        policy.setNoProviderRefundPercentage(request.noProviderRefundPercentage());
        policy.setProviderCancellationRefundPercentage(
                request.providerCancellationRefundPercentage());
        policy.setProviderNoShowRefundPercentage(request.providerNoShowRefundPercentage());
        policy.setServiceFailureRefundPercentage(request.serviceFailureRefundPercentage());
        policy.setCustomerNoShowRefundPercentage(request.customerNoShowRefundPercentage());
        policy.setPlatformFeeRefundable(request.platformFeeRefundable());
        policy.setProcessingFeeRefundable(request.processingFeeRefundable());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setEffectiveTo(request.effectiveTo());
        policy.replaceTiers(request.tiers().stream().map(this::tier).toList());
    }

    private RefundPolicyTier tier(RefundPolicyTierRequest request) {
        RefundPolicyTier tier = new RefundPolicyTier();
        tier.setName(request.name().trim());
        tier.setMinimumMinutesBeforeStart(request.minimumMinutesBeforeStart());
        tier.setRefundPercentage(request.refundPercentage());
        tier.setCancellationFee(request.cancellationFee());
        tier.setPriority(request.priority());
        return tier;
    }

    private Market requireMarket(Long marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(() -> new ResourceNotFoundException("Market was not found"));
    }

    private MarketServiceOffering requireOfferingInMarket(Long marketId, Long offeringId) {
        MarketServiceOffering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Market service was not found"));
        if (!marketId.equals(offering.getMarketId())) {
            throw new ResourceNotFoundException("Market service was not found in this market");
        }
        return offering;
    }

    private RefundPolicy requirePolicy(Long marketId, Long policyId) {
        return policyRepository.findByPolicyIdAndMarketId(policyId, marketId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund policy was not found"));
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RefundPolicyResponse response(RefundPolicy policy) {
        return new RefundPolicyResponse(
                policy.getPolicyId(), policy.getMarketId(), policy.getOfferingId(),
                policy.getVersion(), policy.getName(), policy.getDescription(),
                policy.getCurrencyCode(), policy.getNoProviderRefundPercentage(),
                policy.getProviderCancellationRefundPercentage(),
                policy.getProviderNoShowRefundPercentage(),
                policy.getServiceFailureRefundPercentage(),
                policy.getCustomerNoShowRefundPercentage(),
                policy.isPlatformFeeRefundable(), policy.isProcessingFeeRefundable(),
                policy.getStatus(), policy.getEffectiveFrom(), policy.getEffectiveTo(),
                policy.getTiers().stream().map(item -> new RefundPolicyTierResponse(
                        item.getTierId(), item.getName(), item.getMinimumMinutesBeforeStart(),
                        item.getRefundPercentage(), item.getCancellationFee(), item.getPriority()
                )).toList(), policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
