package com.jobhub.service.customer;

import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.repository.job.JobRepository;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AutoCancelPersistenceTest {
    @Test
    void candidatesArePaidConfirmedUnstartedBookingsWhoseStartHasPassedInThatMarket() {
        try (var factory = new Configuration().addAnnotatedClass(Job.class).addAnnotatedClass(BookingRequest.class)
                .addAnnotatedClass(com.jobhub.entity.job.JobProviderAssignment.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var now = LocalDateTime.of(2026, 11, 23, 15, 0);
            var card = job(em, 1L, now.minusHours(2), "CONFIRMED", "CARD", "PAID", "CONFIRMED");
            var wallet = job(em, 1L, now.minusMinutes(5), "CONFIRMED", "WALLET", "RESERVED", "CONFIRMED");
            var future = job(em, 1L, now.plusMinutes(5), "CONFIRMED", "CARD", "PAID", "CONFIRMED");
            var started = job(em, 1L, now.minusHours(1), "IN_PROGRESS", "CARD", "PAID", "IN_PROGRESS");
            var review = job(em, 1L, now.minusHours(1), "NO_SHOW_REVIEW", "CARD", "PAID", "NO_SHOW_REVIEW");
            var unpaid = job(em, 1L, now.minusHours(1), "CONFIRMED", "CARD", "PENDING", "CONFIRMED");
            var otherMarket = job(em, 2L, now.minusHours(1), "CONFIRMED", "CARD", "PAID", "CONFIRMED");
            var legacy = job(em, 1L, now.minusHours(1), "CONFIRMED", "CARD", "PAID", null);
            var startedAt = job(em, 1L, now.minusHours(1), "CONFIRMED", "CARD", "PAID", "CONFIRMED");
            var beforeFeature = job(em, 1L, now.minusDays(3), "CONFIRMED", "CARD", "PAID", "CONFIRMED");
            startedAt.setActualStartDatetime(now.minusMinutes(50));
            em.flush(); em.clear();

            var jobs = new JpaRepositoryFactory(em).getRepository(JobRepository.class);
            assertThat(jobs.findAutoCancelCandidates(1L, now.minusDays(1), now, PageRequest.of(0, 50)))
                    .containsExactly(card.getJobId(), wallet.getJobId())
                    .doesNotContain(future.getJobId(), started.getJobId(), review.getJobId(), unpaid.getJobId(),
                            otherMarket.getJobId(), legacy.getJobId(), startedAt.getJobId(), beforeFeature.getJobId());
            em.getTransaction().rollback();
        }
    }

    private static Job job(jakarta.persistence.EntityManager em, Long marketId, LocalDateTime start, String status,
                           String method, String paymentStatus, String requestStatus) {
        var job = new Job(); job.setCustomerId(5L); job.setMarketId(marketId); job.setStartDatetime(start);
        job.setJobStatus(status); job.setPaymentMethod(method); job.setPaymentStatus(paymentStatus);
        em.persist(job);
        if (requestStatus != null) {
            var r = new BookingRequest(); r.setUserId(5L); r.setIdempotencyKey(UUID.randomUUID().toString());
            r.setStatus(requestStatus); r.setJobId(job.getJobId());
            em.persist(r);
        }
        return job;
    }
}
