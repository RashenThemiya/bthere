package com.jobhub.service.market;

import com.jobhub.entity.job.JobRate;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.JobRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.*;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.exception.ConflictException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicePricingService {

    private final JobRateRepository rateRepository;
    private final CombinationPricingRules pricingRules;
    private final PlatformFeeService platformFees;

    public Price calculate(Long marketId, Long serviceTypeId, Long optionId,
                           String schedulingModel, LocalDateTime start, LocalDateTime end,
                           BigDecimal distanceKm, BigDecimal areaLatitude,
                           BigDecimal areaLongitude) {
        return calculate(marketId, serviceTypeId, optionId, schedulingModel, start, end,
                distanceKm, areaLatitude, areaLongitude, null);
    }

    public Price calculate(Long marketId, Long serviceTypeId, Long optionId,
                           String schedulingModel, LocalDateTime start, LocalDateTime end,
                           BigDecimal distanceKm, BigDecimal areaLatitude, BigDecimal areaLongitude,
                           List<PricingConditionRequest> requestedSelections) {
        List<PricingConditionRequest> selections = pricingRules.normalize(requestedSelections);
        pricingRules.validateChoices(serviceTypeId, optionId, selections);
        List<JobRate> candidates = applicableRates(marketId, serviceTypeId, optionId,
                schedulingModel, areaLatitude, areaLongitude);
        Map<Long, String> selected = new HashMap<>();
        selections.forEach(c -> selected.put(c.fieldId(), c.choiceValue()));
        Set<Long> pricingFields = new HashSet<>();
        candidates.forEach(rate -> rate.getConditions().forEach(c -> pricingFields.add(c.getFieldId())));
        if (!selected.keySet().equals(pricingFields)) {
            throw new IllegalArgumentException("Select exactly one choice for every applicable pricing field: "
                    + new TreeSet<>(pricingFields));
        }
        Comparator<JobRate> priority = Comparator
                .comparingInt((JobRate rate) -> rate.getConditions().size()).reversed()
                .thenComparing(rate -> isAreaRate(rate) ? rate.getAreaRadiusKm() : null,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        List<JobRate> matching = candidates.stream().filter(rate -> pricingRules.matches(rate, selected))
                .sorted(priority.thenComparing(JobRate::getEffectiveFrom,
                        Comparator.nullsLast(Comparator.reverseOrder()))).toList();

        if (matching.isEmpty()) {
            throw new ResourceNotFoundException("No active price is configured for the selected choices, market and service option");
        }
        JobRate rate = matching.get(0);
        if (!rate.getConditions().isEmpty() && matching.size() > 1
                && priority.compare(rate, matching.get(1)) == 0) {
            throw new ConflictException("Multiple equally specific prices match the selected choices");
        }

        BigDecimal amount = "ROUTE_BASED".equals(schedulingModel)
                ? routeAmount(rate, distanceKm)
                : timeAmount(rate, start, end);
        var fee=platformFees.resolve(rate,serviceTypeId,optionId,amount);
        return new Price(rate.getRateId(), rate.getCurrencyCode(),
                amount.setScale(2, RoundingMode.HALF_UP), rate.getGeographicalAreaName(), selections,
                new RateSnapshot(rate.getBillingType(), rate.getDurationMinutes(), rate.getRate(),
                        rate.getBaseFare(), rate.getPricePerKm(), rate.getMinimumFare(),
                        pricingRules.conditions(rate),fee.percentage(),fee.source()),fee);
    }

    public List<Long> pricingFieldIds(Long marketId, Long serviceTypeId, Long optionId,
                                      String schedulingModel, BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)
                || latitude != null && (latitude.abs().compareTo(new BigDecimal("90")) > 0
                || longitude.abs().compareTo(new BigDecimal("180")) > 0)) {
            throw new IllegalArgumentException("Supply both pricingLatitude and pricingLongitude within valid ranges");
        }
        return applicableRates(marketId, serviceTypeId, optionId, schedulingModel, latitude, longitude)
                .stream().flatMap(rate -> rate.getConditions().stream())
                .map(com.jobhub.entity.job.JobRateCondition::getFieldId).distinct().sorted().toList();
    }

    private List<JobRate> applicableRates(Long marketId, Long serviceTypeId, Long optionId,
                                           String schedulingModel, BigDecimal areaLatitude,
                                           BigDecimal areaLongitude) {
        LocalDateTime now = LocalDateTime.now();
        return rateRepository
                .findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(
                        marketId, serviceTypeId, optionId, "ACTIVE").stream()
                .filter(rate -> rate.getEffectiveFrom() == null || !rate.getEffectiveFrom().isAfter(now))
                .filter(rate -> rate.getEffectiveTo() == null || !rate.getEffectiveTo().isBefore(now))
                .filter(rate -> "ROUTE_BASED".equals(schedulingModel)
                        ? "ROUTE_BASED".equals(rate.getBillingType())
                        : Set.of("HOURLY", "DAILY", "FIXED").contains(rate.getBillingType()))
                .filter(rate -> !isAreaRate(rate) || inside(rate, areaLatitude, areaLongitude))
                .toList();
    }

    private BigDecimal routeAmount(JobRate rate, BigDecimal distanceKm) {
        if (!"ROUTE_BASED".equals(rate.getBillingType()) || rate.getBaseFare() == null
                || rate.getPricePerKm() == null || rate.getMinimumFare() == null) {
            throw new IllegalStateException("Selected route price configuration is incomplete");
        }
        BigDecimal calculated = rate.getBaseFare().add(
                rate.getPricePerKm().multiply(distanceKm == null ? BigDecimal.ZERO : distanceKm));
        return calculated.max(rate.getMinimumFare());
    }

    private BigDecimal timeAmount(JobRate rate, LocalDateTime start, LocalDateTime end) {
        if (rate.getRate() == null) throw new IllegalStateException("Selected time price has no rate");
        long minutes = Math.max(1, Duration.between(start, end).toMinutes());
        return switch (rate.getBillingType()) {
            case "FIXED" -> rate.getRate();
            case "DAILY" -> rate.getRate().multiply(BigDecimal.valueOf(ceil(minutes, 1440)));
            case "HOURLY" -> {
                int increment = rate.getDurationMinutes() == null || rate.getDurationMinutes() < 1
                        ? 60 : rate.getDurationMinutes();
                yield rate.getRate().multiply(BigDecimal.valueOf(ceil(minutes, increment)));
            }
            default -> throw new IllegalStateException("Unsupported time billing type");
        };
    }

    private long ceil(long value, long unit) {
        return (value + unit - 1) / unit;
    }

    private boolean isAreaRate(JobRate rate) {
        return rate.getAreaLatitude() != null && rate.getAreaLongitude() != null
                && rate.getAreaRadiusKm() != null;
    }

    private boolean inside(JobRate rate, BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
                && distanceKm(rate.getAreaLatitude(), rate.getAreaLongitude(), latitude, longitude)
                <= rate.getAreaRadiusKm().doubleValue();
    }

    private double distanceKm(BigDecimal lat1, BigDecimal lon1,
                              BigDecimal lat2, BigDecimal lon2) {
        double latitudeDistance = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double longitudeDistance = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record Price(Long rateId, String currencyCode, BigDecimal amount,
                        String geographicalAreaName, List<PricingConditionRequest> pricingSelections,
                        RateSnapshot pricingSnapshot,PlatformFeeService.Allocation feeAllocation) {
        public Price(Long rateId,String currencyCode,BigDecimal amount,String geographicalAreaName,
                     List<PricingConditionRequest> pricingSelections,RateSnapshot pricingSnapshot) {
            this(rateId,currencyCode,amount,geographicalAreaName,pricingSelections,pricingSnapshot,
                    PlatformFeeService.allocate(amount,pricingSnapshot==null?null:pricingSnapshot.platformFeePercentage(),
                            pricingSnapshot==null?"DEFAULT":pricingSnapshot.platformFeeSource()));
        }
    }

    public record RateSnapshot(String billingType, Integer durationMinutes, BigDecimal rate,
                               BigDecimal baseFare, BigDecimal pricePerKm, BigDecimal minimumFare,
                               List<PricingConditionRequest> conditions,
                               BigDecimal platformFeePercentage,String platformFeeSource) {
        public RateSnapshot(String billingType,Integer durationMinutes,BigDecimal rate,BigDecimal baseFare,
                            BigDecimal pricePerKm,BigDecimal minimumFare,List<PricingConditionRequest> conditions) {
            this(billingType,durationMinutes,rate,baseFare,pricePerKm,minimumFare,conditions,new BigDecimal("0.00"),"DEFAULT");
        }
    }
}
