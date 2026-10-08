package com.jobhub.service.market;

import com.jobhub.dto.market.*;
import com.jobhub.entity.job.JobRate;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.MarketServiceOffering;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

@Service @RequiredArgsConstructor
public class MarketServiceOfferingService {
    private final MarketServiceOfferingRepository offeringRepository;
    private final MarketRepository marketRepository;
    private final ServiceProviderTypeRepository typeRepository;
    private final ServiceOptionRepository optionRepository;
    private final JobRateRepository rateRepository;
    private final AuditService auditService;

    @Transactional
    public MarketServiceResponse create(Long actorId, CreateMarketServiceRequest request) {
        Market market = activeMarket(request.marketId());
        ServiceProviderType type = activeType(request.serviceTypeId());
        if (offeringRepository.existsByMarketIdAndServiceProviderTypeId(
                request.marketId(), request.serviceTypeId())) {
            throw new ConflictException("Service is already configured for this market");
        }
        MarketServiceOffering item = new MarketServiceOffering();
        item.setMarketId(market.getMarketId()); item.setServiceProviderTypeId(type.getServiceProviderTypeId());
        item.setCurrencyCode(market.getDefaultCurrency()); item.setStatus("ACTIVE");
        item = offeringRepository.save(item);
        auditService.record(actorId, "MARKET_SERVICE_CREATED", "MARKET_SERVICE",
                item.getOfferingId(), null, item);
        return response(item, market, type);
    }

    @Transactional(readOnly = true)
    public Page<MarketServiceResponse> list(Long marketId, Pageable pageable) {
        Page<MarketServiceOffering> page = marketId == null
                ? offeringRepository.findAll(pageable)
                : offeringRepository.findAllByMarketId(marketId, pageable);
        return page.map(item -> response(item,
                marketRepository.findById(item.getMarketId()).orElse(null),
                typeRepository.findById(item.getServiceProviderTypeId()).orElse(null)));
    }

    @Transactional
    public MarketServiceResponse status(Long actorId, Long id, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status))
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        MarketServiceOffering item = requireOffering(id);
        String old = item.getStatus(); item.setStatus(status); offeringRepository.save(item);
        auditService.record(actorId, "MARKET_SERVICE_STATUS_UPDATED", "MARKET_SERVICE", id,
                java.util.Map.of("status", old), java.util.Map.of("status", status));
        return response(item, marketRepository.findById(item.getMarketId()).orElse(null),
                typeRepository.findById(item.getServiceProviderTypeId()).orElse(null));
    }

    @Transactional
    public JobRateResponse addRate(Long actorId, Long offeringId, CreateJobRateRequest request) {
        MarketServiceOffering offering = requireOffering(offeringId);
        ServiceOption option = validateRate(offering, request);
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom()))
            throw new IllegalArgumentException("Effective-to cannot be before effective-from");
        JobRate rate = new JobRate();
        rate.setMarketId(offering.getMarketId()); rate.setServiceProviderTypeId(offering.getServiceProviderTypeId());
        rate.setServiceOptionId(option == null ? null : option.getOptionId());
        rate.setCurrencyCode(offering.getCurrencyCode()); rate.setBillingType(request.billingType().trim().toUpperCase());
        rate.setDurationMinutes(request.durationMinutes()); rate.setRate(request.rate());
        applyPricing(rate, request);
        rate.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        rate.setEffectiveTo(request.effectiveTo()); rate.setStatus("ACTIVE");
        rate = rateRepository.save(rate);
        auditService.record(actorId, "JOB_RATE_CREATED", "JOB_RATE", rate.getRateId(), null, rate);
        return rateResponse(rate);
    }

    @Transactional(readOnly = true)
    public Page<JobRateResponse> rates(Long offeringId, Pageable pageable) {
        MarketServiceOffering item = requireOffering(offeringId);
        return rateRepository.findAllByMarketIdAndServiceProviderTypeId(
                item.getMarketId(), item.getServiceProviderTypeId(), pageable).map(this::rateResponse);
    }

    @Transactional
    public JobRateResponse updateRate(Long actorId, Long offeringId, Long rateId,
                                      UpdateJobRateRequest request) {
        MarketServiceOffering offering = requireOffering(offeringId);
        JobRate rate = requireRate(offering, rateId);
        ServiceOption option = validateUpdateRate(offering, request);
        if (request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom()))
            throw new IllegalArgumentException("Effective-to cannot be before effective-from");
        var old = rateResponse(rate);
        rate.setBillingType(request.billingType().trim().toUpperCase());
        rate.setServiceOptionId(option == null ? null : option.getOptionId());
        rate.setDurationMinutes(request.durationMinutes()); rate.setRate(request.rate());
        applyPricing(rate, request);
        rate.setEffectiveFrom(request.effectiveFrom());
        rate.setEffectiveTo(request.effectiveTo()); rateRepository.save(rate);
        auditService.record(actorId, "JOB_RATE_UPDATED", "JOB_RATE", rateId, old, rateResponse(rate));
        return rateResponse(rate);
    }

    @Transactional
    public JobRateResponse rateStatus(Long actorId, Long offeringId, Long rateId, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status))
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        MarketServiceOffering offering = requireOffering(offeringId);
        JobRate rate = requireRate(offering, rateId);
        String old = rate.getStatus(); rate.setStatus(status); rateRepository.save(rate);
        auditService.record(actorId, "JOB_RATE_STATUS_UPDATED", "JOB_RATE", rateId,
                java.util.Map.of("status", old), java.util.Map.of("status", status));
        return rateResponse(rate);
    }

    private Market activeMarket(Long id) { return marketRepository.findById(id)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new ResourceNotFoundException("Active market was not found")); }
    private ServiceProviderType activeType(Long id) { return typeRepository.findById(id)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new ResourceNotFoundException("Active service type was not found")); }
    private MarketServiceOffering requireOffering(Long id) { return offeringRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Market service was not found")); }
    private JobRate requireRate(MarketServiceOffering offering, Long id) {
        JobRate rate = rateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job rate was not found"));
        if (!rate.getMarketId().equals(offering.getMarketId())
                || !rate.getServiceProviderTypeId().equals(offering.getServiceProviderTypeId()))
            throw new ResourceNotFoundException("Job rate was not found for this market service");
        return rate;
    }
    private ServiceOption validateRate(MarketServiceOffering offering, CreateJobRateRequest request) {
        if (request.optionId() == null) {
            if (request.rate() == null)
                throw new IllegalArgumentException("rate is required for legacy service pricing");
            return null;
        }
        ServiceOption option = optionRepository.findByOptionIdAndServiceProviderTypeId(
                        request.optionId(), offering.getServiceProviderTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service option was not found"));
        String billingType = request.billingType().trim().toUpperCase();
        String model = option.getSchedulingModel() == null ? "TIME_BASED" : option.getSchedulingModel();
        if ("ROUTE_BASED".equals(model)) {
            if (!"ROUTE_BASED".equals(billingType) || request.baseFare() == null
                    || request.pricePerKm() == null || request.minimumFare() == null)
                throw new IllegalArgumentException(
                        "ROUTE_BASED pricing requires baseFare, pricePerKm and minimumFare");
        } else {
            if (!Set.of("HOURLY", "DAILY", "FIXED").contains(billingType) || request.rate() == null)
                throw new IllegalArgumentException(
                        "TIME_BASED pricing requires billingType HOURLY, DAILY or FIXED and rate");
        }
        boolean anyArea = request.geographicalAreaName() != null || request.areaLatitude() != null
                || request.areaLongitude() != null || request.areaRadiusKm() != null;
        boolean completeArea = request.geographicalAreaName() != null
                && !request.geographicalAreaName().isBlank()
                && request.areaLatitude() != null && request.areaLongitude() != null
                && request.areaRadiusKm() != null;
        if (anyArea && !completeArea)
            throw new IllegalArgumentException(
                    "Geographical override requires area name, latitude, longitude and radius");
        return option;
    }

    private ServiceOption validateUpdateRate(
            MarketServiceOffering offering,
            UpdateJobRateRequest request
    ) {
        String billingType = request.billingType().trim().toUpperCase();
        ServiceOption option = null;
        String schedulingModel = "TIME_BASED";

        if (request.optionId() != null) {
            option = optionRepository.findByOptionIdAndServiceProviderTypeId(
                            request.optionId(), offering.getServiceProviderTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Service option was not found"));
            schedulingModel = option.getSchedulingModel() == null
                    ? "TIME_BASED"
                    : option.getSchedulingModel();
        }

        if ("ROUTE_BASED".equals(schedulingModel)) {
            if (!"ROUTE_BASED".equals(billingType)
                    || request.baseFare() == null
                    || request.pricePerKm() == null
                    || request.minimumFare() == null) {
                throw new IllegalArgumentException(
                        "ROUTE_BASED pricing requires billingType ROUTE_BASED, "
                                + "baseFare, pricePerKm and minimumFare");
            }
            if (request.rate() != null || request.durationMinutes() != null) {
                throw new IllegalArgumentException(
                        "ROUTE_BASED pricing does not accept rate or durationMinutes");
            }
        } else {
            if (!Set.of("HOURLY", "DAILY", "FIXED").contains(billingType)
                    || request.rate() == null) {
                throw new IllegalArgumentException(
                        "TIME_BASED pricing requires billingType HOURLY, DAILY or FIXED and rate");
            }
            if (request.baseFare() != null
                    || request.pricePerKm() != null
                    || request.minimumFare() != null) {
                throw new IllegalArgumentException(
                        "TIME_BASED pricing does not accept route pricing fields");
            }
        }

        validateGeographicalArea(
                request.geographicalAreaName(),
                request.areaLatitude(),
                request.areaLongitude(),
                request.areaRadiusKm()
        );
        return option;
    }

    private void validateGeographicalArea(
            String name,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            java.math.BigDecimal radiusKm
    ) {
        boolean anyArea = name != null || latitude != null || longitude != null || radiusKm != null;
        boolean completeArea = name != null && !name.isBlank()
                && latitude != null && longitude != null && radiusKm != null;
        if (anyArea && !completeArea) {
            throw new IllegalArgumentException(
                    "Geographical override requires area name, latitude, longitude and radius");
        }
    }
    private void applyPricing(JobRate rate, CreateJobRateRequest request) {
        rate.setBaseFare(request.baseFare());
        rate.setPricePerKm(request.pricePerKm());
        rate.setMinimumFare(request.minimumFare());
        rate.setGeographicalAreaName(request.geographicalAreaName() == null
                ? null : request.geographicalAreaName().trim());
        rate.setAreaLatitude(request.areaLatitude());
        rate.setAreaLongitude(request.areaLongitude());
        rate.setAreaRadiusKm(request.areaRadiusKm());
    }

    private void applyPricing(JobRate rate, UpdateJobRateRequest request) {
        rate.setBaseFare(request.baseFare());
        rate.setPricePerKm(request.pricePerKm());
        rate.setMinimumFare(request.minimumFare());
        rate.setGeographicalAreaName(request.geographicalAreaName() == null
                ? null : request.geographicalAreaName().trim());
        rate.setAreaLatitude(request.areaLatitude());
        rate.setAreaLongitude(request.areaLongitude());
        rate.setAreaRadiusKm(request.areaRadiusKm());
    }
    private MarketServiceResponse response(MarketServiceOffering item, Market market, ServiceProviderType type) {
        return new MarketServiceResponse(item.getOfferingId(), item.getMarketId(), market == null ? null : market.getName(),
                item.getServiceProviderTypeId(), type == null ? null : type.getName(), item.getCurrencyCode(), item.getStatus()); }
    private JobRateResponse rateResponse(JobRate item) { return new JobRateResponse(item.getRateId(), item.getMarketId(),
            item.getServiceProviderTypeId(), item.getServiceOptionId(), item.getCurrencyCode(),
            item.getBillingType(), item.getDurationMinutes(), item.getRate(), item.getBaseFare(),
            item.getPricePerKm(), item.getMinimumFare(), item.getGeographicalAreaName(),
            item.getAreaLatitude(), item.getAreaLongitude(), item.getAreaRadiusKm(),
            item.getEffectiveFrom(), item.getEffectiveTo(), item.getStatus()); }
}
