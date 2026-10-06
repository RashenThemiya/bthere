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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicePricingService {

    private final JobRateRepository rateRepository;

    public Price calculate(Long marketId, Long serviceTypeId, Long optionId,
                           String schedulingModel, LocalDateTime start, LocalDateTime end,
                           BigDecimal distanceKm, BigDecimal areaLatitude,
                           BigDecimal areaLongitude) {
        LocalDateTime now = LocalDateTime.now();
        List<JobRate> candidates = rateRepository
                .findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(
                        marketId, serviceTypeId, optionId, "ACTIVE").stream()
                .filter(rate -> rate.getEffectiveFrom() == null || !rate.getEffectiveFrom().isAfter(now))
                .filter(rate -> rate.getEffectiveTo() == null || !rate.getEffectiveTo().isBefore(now))
                .toList();

        JobRate rate = candidates.stream()
                .filter(item -> isAreaRate(item) && inside(item, areaLatitude, areaLongitude))
                .min(Comparator.comparing(JobRate::getAreaRadiusKm))
                .orElseGet(() -> candidates.stream()
                        .filter(item -> !isAreaRate(item))
                        .max(Comparator.comparing(JobRate::getEffectiveFrom,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No active price is configured for this market and service option")));

        BigDecimal amount = "ROUTE_BASED".equals(schedulingModel)
                ? routeAmount(rate, distanceKm)
                : timeAmount(rate, start, end);
        return new Price(rate.getRateId(), rate.getCurrencyCode(),
                amount.setScale(2, RoundingMode.HALF_UP), rate.getGeographicalAreaName());
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
                        String geographicalAreaName) {}
}
