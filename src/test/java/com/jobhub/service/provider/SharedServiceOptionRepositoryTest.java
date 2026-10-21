package com.jobhub.service.provider;

import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.ServiceOptionRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SharedServiceOptionRepositoryTest {
    private SessionFactory factory;
    private EntityManager em;
    private ServiceOptionRepository repository;

    @BeforeEach void setup() {
        factory = new Configuration().addAnnotatedClass(ServiceOption.class)
                .setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory();
        em = factory.createEntityManager();
        repository = new JpaRepositoryFactory(em).getRepository(ServiceOptionRepository.class);
        em.getTransaction().begin();
    }

    @AfterEach void close() {
        if (em != null) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
        if (factory != null) factory.close();
    }

    private ServiceOption persist(String name, int order) {
        var option = new ServiceOption();
        option.setServiceProviderTypeId(1L);
        option.setCode(name.toUpperCase(Locale.ROOT));
        option.setName(name);
        option.setStatus("ACTIVE");
        option.getServiceTypes().add(new ServiceTypeOption(1L, 20, "ACTIVE"));
        option.getServiceTypes().add(new ServiceTypeOption(2L, order, "ACTIVE"));
        em.persist(option);
        em.flush();
        return option;
    }

    @Test void membershipQueriesUseLinksAndServiceSpecificOrder() {
        var meal = persist("Meal", 9);
        var bathing = persist("Bathing", 1);
        meal.serviceLink(2L).setMapMarkerFieldId(45L);
        em.flush();
        em.clear();
        assertThat(repository.findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(2L, "ACTIVE"))
                .extracting(ServiceOption::getOptionId).containsExactly(bathing.getOptionId(), meal.getOptionId());
        assertThat(repository.findByOptionIdAndServiceProviderTypeId(meal.getOptionId(), 2L)).isPresent();
        assertThat(repository.findById(meal.getOptionId()).orElseThrow().serviceLink(2L).getMapMarkerFieldId()).isEqualTo(45);
        assertThat(repository.findByOptionIdAndServiceProviderTypeId(meal.getOptionId(), 3L)).isEmpty();
        assertThat(repository.existsByServiceProviderTypeIdAndCodeIgnoreCase(2L, "meal")).isTrue();
        assertThat(repository.existsByServiceProviderTypeIdAndCodeIgnoreCase(3L, "meal")).isFalse();
    }

    @Test void inactiveLinkCannotBeSelectedButOtherMembershipRemainsActive() {
        var meal = persist("Meal", 9);
        meal.serviceLink(2L).setStatus("INACTIVE");
        em.flush();
        em.clear();
        var ids = List.of(meal.getOptionId());
        assertThat(repository.findAllByOptionIdInAndServiceProviderTypeIdAndStatus(ids, 2L, "ACTIVE")).isEmpty();
        assertThat(repository.findAllByOptionIdInAndServiceProviderTypeIdAndStatus(ids, 1L, "ACTIVE")).hasSize(1);
        assertThat(repository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscNameAsc(2L)).hasSize(1);
        assertThat(repository.findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(2L, "ACTIVE")).isEmpty();
    }
}
