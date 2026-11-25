package com.jobhub.service.market;

import com.jobhub.entity.market.MarketBanner;
import com.jobhub.repository.market.MarketBannerRepository;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketBannerPersistenceTest {
    @Test
    void bannersAreStoredPerMarketAndReadBackInDisplayOrder() {
        try (var factory = new Configuration().addAnnotatedClass(MarketBanner.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var second = banner(em, 1L, 1, "ACTIVE");
            var first = banner(em, 1L, 0, "ACTIVE");
            var off = banner(em, 1L, 2, "INACTIVE");
            var otherMarket = banner(em, 2L, 0, "ACTIVE");
            first.setEndsAt(Instant.parse("2026-12-31T18:30:00Z"));
            em.flush(); em.clear();

            var repo = new JpaRepositoryFactory(em).getRepository(MarketBannerRepository.class);
            assertThat(repo.findByMarketIdOrderBySortOrderAscBannerIdAsc(1L)).extracting(MarketBanner::getBannerId)
                    .containsExactly(first.getBannerId(), second.getBannerId(), off.getBannerId());
            assertThat(repo.findByMarketIdAndStatusOrderBySortOrderAscBannerIdAsc(1L, "ACTIVE"))
                    .extracting(MarketBanner::getBannerId).containsExactly(first.getBannerId(), second.getBannerId());
            assertThat(repo.countByMarketId(1L)).isEqualTo(3);
            assertThat(repo.findById(first.getBannerId()).orElseThrow().getEndsAt())
                    .isEqualTo(Instant.parse("2026-12-31T18:30:00Z"));
            assertThat(otherMarket.getBannerId()).isNotNull();
            em.getTransaction().rollback();
        }
    }

    private static MarketBanner banner(jakarta.persistence.EntityManager em, Long marketId, int order, String status) {
        var b = new MarketBanner();
        b.setMarketId(marketId); b.setTitle("Banner " + order); b.setImageKey("service-icons/a.jpg");
        b.setAudience("CUSTOMER"); b.setLinkType("NONE"); b.setSortOrder(order); b.setStatus(status);
        em.persist(b);
        return b;
    }
}
