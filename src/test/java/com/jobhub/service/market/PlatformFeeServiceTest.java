package com.jobhub.service.market;

import com.jobhub.dto.market.PlatformFeeRequest;
import com.jobhub.entity.job.JobRate;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.audit.AuditService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformFeeServiceTest {
    ServiceProviderTypeRepository services=mock(ServiceProviderTypeRepository.class);
    ServiceOptionRepository options=mock(ServiceOptionRepository.class);
    AuditService audit=mock(AuditService.class);
    PlatformFeeService fees=new PlatformFeeService(services,options,audit);
    ServiceProviderType type;
    ServiceOption option;
    JobRate rate=new JobRate();
    @BeforeEach void setup(){
        type=new ServiceProviderType();type.setServiceProviderTypeId(2L);type.setPlatformFeePercentage(new BigDecimal("12.50"));
        option=new ServiceOption();option.setOptionId(12L);option.setServiceProviderTypeId(2L);
        when(services.findById(2L)).thenReturn(Optional.of(type));when(services.findLockedById(2L)).thenReturn(Optional.of(type));
        when(options.findById(12L)).thenReturn(Optional.of(option));when(options.findLockedById(12L)).thenReturn(Optional.of(option));
    }
    PlatformFeeService.Allocation allocation(){return fees.resolve(rate,2L,12L,new BigDecimal("1000.00"));}
    @Test void serviceDefaultDeductsCommissionWithoutIncreasingCustomerFare(){
        var result=allocation();assertThat(result.source()).isEqualTo("SERVICE");
        assertThat(result.platformFee()).isEqualByComparingTo("125.00");assertThat(result.providerEarnings()).isEqualByComparingTo("875.00");
        assertThat(result.platformFee().add(result.providerEarnings())).isEqualByComparingTo("1000.00");
    }
    @Test void optionOverridesServiceAndMatchedVariantRateOverridesOption(){
        option.setPlatformFeePercentage(new BigDecimal("10"));assertThat(allocation().platformFee()).isEqualByComparingTo("100");
        assertThat(allocation().source()).isEqualTo("OPTION");
        rate.setPlatformFeePercentage(new BigDecimal("7.25"));assertThat(allocation().platformFee()).isEqualByComparingTo("72.50");
        assertThat(allocation().source()).isEqualTo("RATE");
    }
    @Test void explicitZeroDoesNotFallThroughAndNullRestoresInheritance(){
        option.setPlatformFeePercentage(BigDecimal.ZERO);assertThat(allocation().platformFee()).isZero();
        assertThat(allocation().source()).isEqualTo("OPTION");
        option.setPlatformFeePercentage(null);assertThat(allocation().platformFee()).isEqualByComparingTo("125");
        type.setPlatformFeePercentage(null);assertThat(allocation().platformFee()).isZero();assertThat(allocation().source()).isEqualTo("DEFAULT");
    }
    @Test void sharedOptionUsesCanonicalServiceDefaultThroughAnotherNavigationService(){
        var result=fees.resolve(rate,99L,12L,new BigDecimal("1000"));
        assertThat(result.platformFee()).isEqualByComparingTo("125");verify(services,never()).findById(99L);
    }
    @Test void legacyServiceRateCanInheritServiceDefaultWithoutOption(){
        assertThat(fees.resolve(rate,2L,null,new BigDecimal("1000")).source()).isEqualTo("SERVICE");
    }
    @Test void roundedSplitConservesEveryCentAndAllowsFullCommission(){
        var tiny=PlatformFeeService.allocate(new BigDecimal("0.05"),new BigDecimal("10"),"RATE");
        assertThat(tiny.platformFee()).isEqualByComparingTo("0.01");assertThat(tiny.providerEarnings()).isEqualByComparingTo("0.04");
        var full=PlatformFeeService.allocate(new BigDecimal("19.99"),new BigDecimal("100"),"RATE");
        assertThat(full.providerEarnings()).isZero();assertThat(full.platformFee()).isEqualByComparingTo("19.99");
    }
    @Test void invalidPercentagesAreRejectedBeforeConfigurationChanges(){
        for(String invalid:new String[]{"-0.01","100.01","1.234"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> fees.updateOption(1L,12L,new PlatformFeeRequest(new BigDecimal(invalid))));
        }
        assertThat(option.getPlatformFeePercentage()).isNull();verifyNoInteractions(audit);
    }
    @Test void configurationWritesAreAuditedAndClearingAnOptionRevealsServiceDefault(){
        fees.updateService(1L,2L,new PlatformFeeRequest(new BigDecimal("15")));
        var override=fees.updateOption(1L,12L,new PlatformFeeRequest(BigDecimal.ZERO));
        assertThat(override.effectivePlatformFeePercentage()).isZero();
        var inherited=fees.updateOption(1L,12L,new PlatformFeeRequest(null));
        assertThat(inherited.platformFeePercentage()).isNull();assertThat(inherited.effectivePlatformFeePercentage()).isEqualByComparingTo("15");
        verify(audit).record(eq(1L),eq("SERVICE_PLATFORM_FEE_UPDATED"),anyString(),eq(2L),any(),any());
        verify(audit,times(2)).record(eq(1L),eq("OPTION_PLATFORM_FEE_UPDATED"),anyString(),eq(12L),any(),any());
    }
}
