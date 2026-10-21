package com.jobhub.service.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.entity.job.*;
import com.jobhub.entity.provider.ServiceCustomField;
import com.jobhub.exception.*;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.provider.ServiceCustomFieldRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicePricingServiceTest {
    @Mock JobRateRepository rates;
    @Mock ServiceCustomFieldRepository fields;
    ServicePricingService service;
    List<JobRate> configured = new ArrayList<>();
    LocalDateTime start = LocalDateTime.now().plusDays(1);

    @BeforeEach void setup() {
        service = new ServicePricingService(rates, new CombinationPricingRules(fields, rates, new ObjectMapper()));
        when(rates.findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(1L, 2L, 12L, "ACTIVE"))
                .thenReturn(configured);
    }

    void field(long id, String options) {
        var f = new ServiceCustomField(); f.setFieldId(id); f.setStatus("ACTIVE"); f.setScope("PROVIDER_REQUIREMENT");
        f.setFieldType("SELECT"); f.setCustomerVisible(true); f.setSearchable(true); f.setOptionsJson(options);
        when(fields.findByFieldIdAndServiceProviderTypeId(id, 2L)).thenReturn(Optional.of(f));
    }

    JobRate rate(long id, String amount, PricingConditionRequest... conditions) {
        var rate = new JobRate(); rate.setRateId(id); rate.setCurrencyCode("LKR"); rate.setBillingType("FIXED");
        rate.setRate(new BigDecimal(amount)); rate.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        for (var c : conditions) rate.getConditions().add(new JobRateCondition(c.fieldId(), c.choiceValue()));
        configured.add(rate); return rate;
    }

    PricingConditionRequest choice(long id, String value) { return new PricingConditionRequest(id, value); }

    ServicePricingService.Price price(List<PricingConditionRequest> selections) {
        return service.calculate(1L, 2L, 12L, "TIME_BASED", start, start.plusHours(1), null, null, null, selections);
    }

    @Test void combinationOverridesSingleChoiceAndDefaultRateAndSnapshotsPrice() {
        field(45, "[\"CAR\",\"BIKE\"]"); field(46, "[\"MALE\",\"FEMALE\"]");
        rate(1, "100"); rate(2, "200", choice(45, "CAR"));
        var combination = rate(3, "300", choice(45, "CAR"), choice(46, "MALE"));
        var price = price(List.of(choice(46, "MALE"), choice(45, "CAR")));
        assertThat(price.rateId()).isEqualTo(3);
        assertThat(price.amount()).isEqualByComparingTo("300.00");
        combination.setRate(BigDecimal.ONE);
        assertThat(price.pricingSnapshot().rate()).isEqualByComparingTo("300");
        assertThat(price.pricingSelections()).extracting(PricingConditionRequest::fieldId).containsExactly(45L, 46L);
    }

    @Test void missingSelectionsCannotBypassCombinationPriceUsingDefault() {
        rate(1, "100"); rate(2, "200", choice(45, "CAR"));
        assertThatIllegalArgumentException().isThrownBy(() -> price(null));
    }

    @Test void noMatchingCombinationFailsInsteadOfChoosingAnotherVehiclePrice() {
        field(45, "[\"CAR\",\"BIKE\"]"); rate(2, "200", choice(45, "CAR"));
        assertThatThrownBy(() -> price(List.of(choice(45, "BIKE")))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void equallySpecificMatchingRulesFailClosed() {
        field(45, "[\"CAR\"]"); field(46, "[\"MALE\"]");
        rate(1, "100", choice(45, "CAR")); rate(2, "200", choice(46, "MALE"));
        assertThatThrownBy(() -> price(List.of(choice(45, "CAR"), choice(46, "MALE"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test void legacyPricingKeepsLatestDefaultAndExistingTimeRounding() {
        var old = rate(1, "100"); old.setEffectiveFrom(LocalDateTime.now().minusDays(2));
        var latest = rate(2, "200"); latest.setBillingType("HOURLY"); latest.setDurationMinutes(30);
        assertThat(price(null).amount()).isEqualByComparingTo("400.00");
    }

    @Test void routePricingUsesCombinationAndMinimumFare() {
        field(45, "[\"CAR\"]");
        var route = rate(1, "0", choice(45, "CAR")); route.setBillingType("ROUTE_BASED");
        route.setBaseFare(new BigDecimal("250")); route.setPricePerKm(new BigDecimal("100")); route.setMinimumFare(new BigDecimal("500"));
        assertThat(service.calculate(1L, 2L, 12L, "ROUTE_BASED", start, start.plusMinutes(10),
                BigDecimal.ONE, null, null, List.of(choice(45, "CAR"))).amount()).isEqualByComparingTo("500.00");
    }

    @Test void smallerAreaOverridesSameSpecificityAndOutsideAreasDoNotRequireChoices() {
        rate(1, "100");
        var area = rate(2, "200", choice(45, "CAR"));
        area.setAreaLatitude(BigDecimal.ZERO); area.setAreaLongitude(BigDecimal.ZERO); area.setAreaRadiusKm(BigDecimal.TEN);
        assertThat(price(null).rateId()).isEqualTo(1);
        field(45, "[\"CAR\"]");
        assertThat(service.calculate(1L, 2L, 12L, "TIME_BASED", start, start.plusHours(1), null,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(choice(45, "CAR"))).rateId()).isEqualTo(2);
    }
}
