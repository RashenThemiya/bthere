package com.jobhub.service.market;

import com.jobhub.dto.market.UpdateJobRateRequest;
import com.jobhub.entity.job.JobRate;
import com.jobhub.entity.market.MarketServiceOffering;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketServiceOfferingServiceTest {

    @Mock private MarketServiceOfferingRepository offeringRepository;
    @Mock private MarketRepository marketRepository;
    @Mock private ServiceProviderTypeRepository typeRepository;
    @Mock private ServiceOptionRepository optionRepository;
    @Mock private JobRateRepository rateRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private MarketServiceOfferingService service;

    @Test
    void updateIsACompleteReplacementAndNullOptionClearsExistingOption() {
        MarketServiceOffering offering = offering();
        JobRate rate = rate();
        when(offeringRepository.findById(7L)).thenReturn(Optional.of(offering));
        when(rateRepository.findById(9L)).thenReturn(Optional.of(rate));

        LocalDateTime effectiveFrom = LocalDateTime.of(2026, 11, 1, 0, 0);
        UpdateJobRateRequest request = new UpdateJobRateRequest(
                "fixed", null, new BigDecimal("3500.00"), effectiveFrom, null,
                null, null, null, null,
                null, null, null, null
        );

        service.updateRate(1L, 7L, 9L, request);

        assertThat(rate.getServiceOptionId()).isNull();
        assertThat(rate.getBillingType()).isEqualTo("FIXED");
        assertThat(rate.getRate()).isEqualByComparingTo("3500.00");
        assertThat(rate.getEffectiveFrom()).isEqualTo(effectiveFrom);
        assertThat(rate.getEffectiveTo()).isNull();
        assertThat(rate.getBaseFare()).isNull();
        assertThat(rate.getGeographicalAreaName()).isNull();
        verify(rateRepository).save(rate);
    }

    @Test
    void updateValidatesEffectiveToAgainstRequiredEffectiveFrom() {
        MarketServiceOffering offering = offering();
        JobRate rate = rate();
        when(offeringRepository.findById(7L)).thenReturn(Optional.of(offering));
        when(rateRepository.findById(9L)).thenReturn(Optional.of(rate));

        UpdateJobRateRequest request = new UpdateJobRateRequest(
                "HOURLY", 60, new BigDecimal("1000"),
                LocalDateTime.of(2026, 11, 2, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0),
                null, null, null, null,
                null, null, null, null
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateRate(1L, 7L, 9L, request))
                .withMessage("Effective-to cannot be before effective-from");
    }

    private MarketServiceOffering offering() {
        MarketServiceOffering offering = new MarketServiceOffering();
        offering.setOfferingId(7L);
        offering.setMarketId(2L);
        offering.setServiceProviderTypeId(3L);
        offering.setCurrencyCode("LKR");
        return offering;
    }

    private JobRate rate() {
        JobRate rate = new JobRate();
        rate.setRateId(9L);
        rate.setMarketId(2L);
        rate.setServiceProviderTypeId(3L);
        rate.setServiceOptionId(44L);
        rate.setCurrencyCode("LKR");
        rate.setBillingType("ROUTE_BASED");
        rate.setBaseFare(new BigDecimal("500"));
        rate.setPricePerKm(new BigDecimal("100"));
        rate.setMinimumFare(new BigDecimal("1000"));
        rate.setGeographicalAreaName("Old area");
        rate.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        rate.setEffectiveTo(LocalDateTime.of(2026, 12, 31, 0, 0));
        rate.setStatus("ACTIVE");
        return rate;
    }
}
