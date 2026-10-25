package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.dto.provider.ProviderTypeResponse;
import com.jobhub.entity.market.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.provider.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerCatalogTransactionTest {
    private final MarketRepository markets = mock(MarketRepository.class);
    private final MarketServiceOfferingRepository offerings = mock(MarketServiceOfferingRepository.class);
    private final ServiceProviderTypeRepository serviceTypes = mock(ServiceProviderTypeRepository.class);
    private final ProviderTypeService types = mock(ProviderTypeService.class);
    private final CustomerProfileService profiles = mock(CustomerProfileService.class);
    private final CustomerRequirementsService requirements = mock(CustomerRequirementsService.class);
    private final ServiceOptionService options = mock(ServiceOptionService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private CustomerDiscoveryService catalog;

    @BeforeEach void setup() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var transactions = new DataSourceTransactionManager(dataSource);
        var availability = transactional(new CustomerAvailabilityService(markets, offerings, serviceTypes,
                mock(ServiceProviderRepository.class), mock(ServiceProviderMarketRepository.class),
                mock(ProviderServiceOptionRepository.class), mock(ServiceProviderAvailabilityRepository.class),
                mock(ServiceOptionRepository.class), mock(JobRepository.class)), transactions);
        catalog = transactional(new CustomerDiscoveryService(profiles, requirements, availability, types, options,
                mock(ServiceCustomFieldService.class), mock(ServiceOptionRepository.class),
                mock(ServiceProviderTypeAssignmentRepository.class), mock(ServiceProviderRepository.class),
                mock(ServiceProviderServiceAreaRepository.class), mock(ProviderServiceLanguageRepository.class),
                mock(ProviderServiceSkillRepository.class), mock(ServiceTypeSkillRepository.class),
                mock(ProviderSearchFields.class), mock(MapMarkerResolver.class),
                mock(com.jobhub.service.market.ServicePricingService.class)), transactions);
        var market = new Market(); market.setMarketId(1L); market.setStatus("ACTIVE");
        lenient().when(markets.findById(1L)).thenReturn(Optional.of(market));
    }

    private <T> T transactional(T target, DataSourceTransactionManager manager) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked") T proxy = (T) factory.getProxy();
        return proxy;
    }

    private ProviderTypeResponse type(long id) {
        var entity = new ServiceProviderType(); entity.setServiceProviderTypeId(id); entity.setStatus("ACTIVE");
        lenient().when(serviceTypes.findById(id)).thenReturn(Optional.of(entity));
        return new ProviderTypeResponse(id, "Service " + id, null, null, "ACTIVE", null, null, List.of(), List.of());
    }

    private void offered(long id) {
        var offering = new MarketServiceOffering(); offering.setStatus("ACTIVE");
        when(offerings.findByMarketIdAndServiceProviderTypeId(1L, id)).thenReturn(Optional.of(offering));
    }

    @Test void mixedMarketCatalogSkipsMissingOfferingsWithoutRollingBack() throws Exception {
        var activeTypes = List.of(type(3), type(4));
        when(types.listActiveTypes()).thenReturn(activeTypes);
        offered(3);
        when(requirements.eligibility(25L, 3L, null)).thenReturn(new Eligibility(true, List.of()));
        var result = catalog.services(25L, 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).service().id()).isEqualTo(3L);
        assertThat(result.get(0).access().status()).isEqualTo("ELIGIBLE");
        verify(requirements, never()).eligibility(25L, 4L, null);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test void allUnofferedServicesReturnEmptyCatalog() throws Exception {
        var activeTypes = List.of(type(3));
        when(types.listActiveTypes()).thenReturn(activeTypes);
        assertThat(catalog.services(25L, 1L)).isEmpty();
        verify(connection).commit();
    }

    @Test void offeredServicesRetainAccessRestrictions() {
        var activeTypes = List.of(type(3));
        when(types.listActiveTypes()).thenReturn(activeTypes);
        offered(3);
        when(requirements.eligibility(25L, 3L, null)).thenReturn(new Eligibility(false,
                List.of(new Reason("MISSING", "NIC", "Provide NIC"))));
        var result = catalog.services(25L, 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).access().eligibility().eligible()).isFalse();
        assertThat(result.get(0).access().eligibility().reasons().get(0).requirementCode()).isEqualTo("NIC");
    }

    @Test void invalidMarketStillReturnsNotFound() {
        assertThatThrownBy(() -> catalog.services(25L, 99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(types, requirements);
    }

    @Test void inactiveOfferingsAreOmittedWithoutEvaluatingAccess() throws Exception {
        var activeTypes = List.of(type(3));
        when(types.listActiveTypes()).thenReturn(activeTypes);
        var offering = new MarketServiceOffering(); offering.setStatus("INACTIVE");
        when(offerings.findByMarketIdAndServiceProviderTypeId(1L, 3L)).thenReturn(Optional.of(offering));
        assertThat(catalog.services(25L, 1L)).isEmpty();
        verifyNoInteractions(requirements);
        verify(connection).commit();
    }

    @Test void optionRestrictionsProduceCheckOptionWithoutHidingService() {
        var activeTypes = List.of(type(3));
        when(types.listActiveTypes()).thenReturn(activeTypes);
        offered(3);
        when(requirements.eligibility(25L, 3L, null)).thenReturn(new Eligibility(true, List.of()));
        var option = mock(com.jobhub.dto.provider.ServiceOptionResponse.class);
        when(option.optionId()).thenReturn(12L);
        when(options.list(3L, false)).thenReturn(List.of(option));
        when(requirements.eligibility(25L, 3L, 12L)).thenReturn(new Eligibility(false,
                List.of(new Reason("MINIMUM_AGE", "DATE_OF_BIRTH", "Minimum age is 21"))));
        var result = catalog.services(25L, 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).access().status()).isEqualTo("CHECK_OPTION");
        assertThat(result.get(0).access().action()).isEqualTo("SELECT_OPTION");
    }

    @Test void missingCustomerProfileIsNotSwallowed() {
        doThrow(new ResourceNotFoundException("Complete the customer profile first")).when(profiles).get(25L);
        assertThatThrownBy(() -> catalog.services(25L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(markets, types, requirements);
    }
}
