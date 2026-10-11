package com.jobhub.service.market;

import com.jobhub.dto.market.RefundPolicyRequest;
import com.jobhub.dto.market.RefundPolicyTierRequest;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.MarketServiceOffering;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundPolicyServiceTest {
    @Mock RefundPolicyRepository policyRepository;
    @Mock MarketRepository marketRepository;
    @Mock MarketServiceOfferingRepository offeringRepository;
    @Mock AuditService auditService;

    private RefundPolicyService service;
    private Market market;

    @BeforeEach
    void setUp() {
        service = new RefundPolicyService(policyRepository, marketRepository,
                offeringRepository, auditService);
        market = new Market();
        market.setMarketId(1L);
        market.setDefaultCurrency("LKR");
        market.setStatus("ACTIVE");
        when(marketRepository.findById(1L)).thenReturn(Optional.of(market));
    }

    @Test
    void createsVersionedMarketDefaultAsDraft() {
        RefundPolicy previous = policy(4L, 1L, null, "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"), null);
        previous.setVersion(2);
        when(policyRepository.findTopByMarketIdOrderByVersionDesc(1L))
                .thenReturn(Optional.of(previous));
        when(policyRepository.save(any())).thenAnswer(invocation -> {
            RefundPolicy saved = invocation.getArgument(0);
            saved.setPolicyId(5L);
            return saved;
        });

        var result = service.create(9L, 1L, request(null));

        assertEquals(3, result.version());
        assertEquals("DRAFT", result.status());
        assertEquals("LKR", result.currencyCode());
        assertNull(result.offeringId());
        assertEquals(3, result.tiers().size());
    }

    @Test
    void rejectsPolicyWithoutCatchAllTier() {
        RefundPolicyRequest invalid = new RefundPolicyRequest(null, "Policy", null,
                percent(100), percent(100), percent(100), percent(100), percent(0),
                true, false, Instant.parse("2026-11-01T00:00:00Z"), null,
                List.of(new RefundPolicyTierRequest("Early", 60, percent(100),
                        BigDecimal.ZERO, 1)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.create(9L, 1L, invalid));
        assertTrue(exception.getMessage().contains("minimumMinutesBeforeStart 0"));
    }

    @Test
    void resolvesServiceOverrideBeforeMarketDefault() {
        Instant past = Instant.now().minusSeconds(3600);
        RefundPolicy marketDefault = policy(1L, 1L, null, "ACTIVE", past, null);
        RefundPolicy override = policy(2L, 1L, 10L, "ACTIVE", past, null);
        MarketServiceOffering offering = new MarketServiceOffering();
        offering.setOfferingId(10L);
        offering.setMarketId(1L);
        when(offeringRepository.findById(10L)).thenReturn(Optional.of(offering));
        when(policyRepository.findAllByMarketIdAndStatus(1L, "ACTIVE"))
                .thenReturn(List.of(marketDefault, override));

        assertEquals(2L, service.resolve(1L, 10L).policyId());
    }

    @Test
    void resolvesMarketDefaultWhenNoOverrideExists() {
        RefundPolicy marketDefault = policy(1L, 1L, null, "ACTIVE",
                Instant.now().minusSeconds(3600), null);
        MarketServiceOffering offering = new MarketServiceOffering();
        offering.setOfferingId(10L);
        offering.setMarketId(1L);
        when(offeringRepository.findById(10L)).thenReturn(Optional.of(offering));
        when(policyRepository.findAllByMarketIdAndStatus(1L, "ACTIVE"))
                .thenReturn(List.of(marketDefault));

        assertEquals(1L, service.resolve(1L, 10L).policyId());
    }

    @Test
    void reportsMissingEffectivePolicy() {
        when(policyRepository.findAllByMarketIdAndStatus(1L, "ACTIVE"))
                .thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.resolve(1L, null));
    }

    private RefundPolicyRequest request(Long offeringId) {
        return new RefundPolicyRequest(offeringId, "Standard policy", "Description",
                percent(100), percent(100), percent(100), percent(100), percent(0),
                true, false, Instant.parse("2026-11-01T00:00:00Z"), null,
                List.of(
                        new RefundPolicyTierRequest("Early", 1440, percent(100),
                                BigDecimal.ZERO, 1),
                        new RefundPolicyTierRequest("Medium", 240, percent(50),
                                BigDecimal.ZERO, 2),
                        new RefundPolicyTierRequest("Late", 0, percent(0),
                                BigDecimal.ZERO, 3)));
    }

    private RefundPolicy policy(Long id, Long marketId, Long offeringId, String status,
                                Instant from, Instant to) {
        RefundPolicy policy = new RefundPolicy();
        policy.setPolicyId(id);
        policy.setMarketId(marketId);
        policy.setOfferingId(offeringId);
        policy.setVersion(id.intValue());
        policy.setName("Policy " + id);
        policy.setCurrencyCode("LKR");
        policy.setNoProviderRefundPercentage(percent(100));
        policy.setProviderCancellationRefundPercentage(percent(100));
        policy.setProviderNoShowRefundPercentage(percent(100));
        policy.setServiceFailureRefundPercentage(percent(100));
        policy.setCustomerNoShowRefundPercentage(percent(0));
        policy.setStatus(status);
        policy.setEffectiveFrom(from);
        policy.setEffectiveTo(to);
        return policy;
    }

    private BigDecimal percent(int value) {
        return BigDecimal.valueOf(value);
    }
}
