package com.jobhub.service.market;

import com.jobhub.dto.market.PlatformFeeRequest;
import com.jobhub.entity.job.JobRate;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PlatformFeeService {
    private final ServiceProviderTypeRepository services;
    private final ServiceOptionRepository options;
    private final AuditService audit;

    public record Configuration(String scope,Long scopeId,Long configurationServiceTypeId,
            BigDecimal platformFeePercentage,BigDecimal effectivePlatformFeePercentage,String source) {}
    public record Allocation(BigDecimal percentage,String source,BigDecimal platformFee,BigDecimal providerEarnings) {}

    public Configuration serviceConfiguration(Long id) {
        return serviceView(services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service not found")));
    }
    public Configuration optionConfiguration(Long id) {
        return optionView(options.findById(id).orElseThrow(() -> new ResourceNotFoundException("Option not found")));
    }
    @Transactional
    public Configuration updateService(Long actorId,Long id,PlatformFeeRequest input) {
        var service=services.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        var old=serviceView(service);service.setPlatformFeePercentage(normalize(input.platformFeePercentage()));
        var result=serviceView(service);
        audit.record(actorId,"SERVICE_PLATFORM_FEE_UPDATED","SERVICE_PROVIDER_TYPE",id,old,result);return result;
    }
    @Transactional
    public Configuration updateOption(Long actorId,Long id,PlatformFeeRequest input) {
        var option=options.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Option not found"));
        var old=optionView(option);option.setPlatformFeePercentage(normalize(input.platformFeePercentage()));
        var result=optionView(option);
        audit.record(actorId,"OPTION_PLATFORM_FEE_UPDATED","SERVICE_OPTION",id,old,result);return result;
    }
    public Allocation resolve(JobRate rate,Long serviceId,Long optionId,BigDecimal fare) {
        if(rate.getPlatformFeePercentage()!=null)return allocate(fare,rate.getPlatformFeePercentage(),"RATE");
        if(optionId!=null) {
            var option=options.findById(optionId).orElseThrow(() -> new ResourceNotFoundException("Option not found"));
            if(option.getPlatformFeePercentage()!=null)return allocate(fare,option.getPlatformFeePercentage(),"OPTION");
            // Shared option links must use the same canonical service default.
            serviceId=option.getServiceProviderTypeId();
        }
        var service=services.findById(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        return allocate(fare,service.getPlatformFeePercentage(),service.getPlatformFeePercentage()==null?"DEFAULT":"SERVICE");
    }
    private Configuration serviceView(ServiceProviderType service) {
        var p=service.getPlatformFeePercentage();
        return new Configuration("SERVICE",service.getServiceProviderTypeId(),service.getServiceProviderTypeId(),p,
                p==null?new BigDecimal("0.00"):normalize(p),p==null?"DEFAULT":"SERVICE");
    }
    private Configuration optionView(ServiceOption option) {
        var p=option.getPlatformFeePercentage();
        var inherited=serviceConfiguration(option.getServiceProviderTypeId());
        return new Configuration("OPTION",option.getOptionId(),option.getServiceProviderTypeId(),p,
                p==null?inherited.effectivePlatformFeePercentage():normalize(p),p==null?inherited.source():"OPTION");
    }
    public static BigDecimal normalize(BigDecimal percentage) {
        if(percentage==null)return null;
        if(percentage.signum()<0 || percentage.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException("Platform fee percentage must be between 0 and 100");
        try{return percentage.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException e){throw new IllegalArgumentException("Platform fee percentage allows at most two decimal places");}
    }
    public static Allocation allocate(BigDecimal fare,BigDecimal percentage,String source) {
        if(fare==null || fare.signum()<0)throw new IllegalArgumentException("Fare must be nonnegative");
        var amount=fare.setScale(2,RoundingMode.HALF_UP);
        var p=percentage==null?new BigDecimal("0.00"):normalize(percentage);
        var fee=amount.multiply(p).movePointLeft(2).setScale(2,RoundingMode.HALF_UP);
        return new Allocation(p,source,fee,amount.subtract(fee));
    }
}
