package com.jobhub.service.provider;

import com.jobhub.entity.job.JobRate;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.job.JobRateRepository;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class FullySharedOptionPersistenceTest {
    @Test void mergedFieldAssociationCanServeAnOptionInAnotherHistoricalNamespace() {
        try(var factory=new Configuration().addAnnotatedClass(ServiceCustomField.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class","org.h2.Driver")
                .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto","create-drop").buildSessionFactory();var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            var field=new ServiceCustomField();field.setServiceProviderTypeId(7L);field.setCode("VEHICLE_TYPE");
            field.setStatus("ACTIVE");field.setScope("PROVIDER_REQUIREMENT");field.setOptionIds(java.util.Set.of(12L,13L));
            em.persist(field);em.flush();em.clear();
            var repo=new JpaRepositoryFactory(em).getRepository(com.jobhub.repository.provider.ServiceCustomFieldRepository.class);
            assertThat(repo.findSharedForOption(12L)).extracting(ServiceCustomField::getFieldId).containsExactly(field.getFieldId());
            assertThat(repo.findSharedForOption(13L)).extracting(ServiceCustomField::getFieldId).containsExactly(field.getFieldId());
            assertThat(repo.findSharedForOption(14L)).isEmpty();
            em.getTransaction().rollback();
        }
    }
    @Test void linkedOfferingReadsTheSameAuthoritativePricesWithoutLegacyDuplicates() {
        try (var factory = new Configuration().addAnnotatedClass(JobRate.class).addAnnotatedClass(ServiceOption.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var option = new ServiceOption(); option.setServiceProviderTypeId(1L); option.setCode("CARE_RIDE");
            option.getServiceTypes().add(new ServiceTypeOption(1L,0,"ACTIVE"));
            option.getServiceTypes().add(new ServiceTypeOption(2L,7,"ACTIVE"));
            em.persist(option);
            var shared = rate(1L, option.getOptionId()); em.persist(shared);
            var oldDuplicate = rate(2L, option.getOptionId()); em.persist(oldDuplicate);
            em.flush(); em.clear();
            var repo = new JpaRepositoryFactory(em).getRepository(JobRateRepository.class);
            assertThat(repo.findSharedRates(1L,1L,PageRequest.of(0,20)).getContent())
                    .extracting(JobRate::getRateId).containsExactly(shared.getRateId());
            assertThat(repo.findSharedRates(1L,2L,PageRequest.of(0,20)).getContent())
                    .extracting(JobRate::getRateId).containsExactly(shared.getRateId());
            assertThat(repo.findSharedRates(1L,3L,PageRequest.of(0,20))).isEmpty();
            em.getTransaction().rollback();
        }
    }

    @Test void databaseRejectsDuplicateProviderOptionEnrollmentAcrossAssignments() {
        try (var factory = new Configuration().addAnnotatedClass(ProviderServiceOption.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var first = new ProviderServiceOption(); first.setProviderId(15L); first.setAssignmentId(10L); first.setOptionId(12L);
            em.persist(first); em.flush();
            var duplicate = new ProviderServiceOption(); duplicate.setProviderId(15L); duplicate.setAssignmentId(11L); duplicate.setOptionId(12L);
            assertThatThrownBy(() -> { em.persist(duplicate); em.flush(); })
                    .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
            em.getTransaction().rollback();
        }
    }

    private JobRate rate(Long service, Long option) {
        var rate = new JobRate(); rate.setMarketId(1L); rate.setServiceProviderTypeId(service);
        rate.setServiceOptionId(option); rate.setStatus("ACTIVE"); rate.setBillingType("FIXED");
        return rate;
    }
}
