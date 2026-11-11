package com.jobhub.service.market;

import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Booking confirmation snapshots the refund policy inside its own transaction. With no active policy the lookup
 * must not poison that transaction (request #85: confirm-mock-card returned 500 and rolled back).
 */
class RefundPolicyTransactionTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()));
        }
        @Bean RefundPolicyRepository policies() {
            var repository = mock(RefundPolicyRepository.class);
            when(repository.findAllByMarketIdAndStatus(1L, "ACTIVE")).thenReturn(List.of());
            return repository;
        }
        @Bean MarketRepository markets() {
            var repository = mock(MarketRepository.class);
            when(repository.findById(1L)).thenReturn(Optional.of(new com.jobhub.entity.market.Market()));
            return repository;
        }
        @Bean RefundPolicyService refundPolicyService(RefundPolicyRepository policies, MarketRepository markets) {
            return new RefundPolicyService(policies, markets, mock(MarketServiceOfferingRepository.class), mock(AuditService.class));
        }
    }

    @Test void missingPolicyDuringConfirmationLeavesTheBookingTransactionCommittable() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var service = context.getBean(RefundPolicyService.class);
            var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            Optional<Long> policyId = outer.execute(status -> service.effectivePolicyId(1L, null));
            assertThat(policyId).isEmpty();

            // The old pattern: catching resolve()'s exception still leaves the outer transaction rollback-only.
            assertThatThrownBy(() -> outer.execute(status -> {
                try { return service.resolve(1L, null); } catch (ResourceNotFoundException none) { return null; }
            })).isInstanceOf(UnexpectedRollbackException.class);
        }
    }
}
