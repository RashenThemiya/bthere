package com.jobhub.service.chat;

import com.jobhub.entity.chat.ChatMessage;
import com.jobhub.entity.chat.ChatReadState;
import com.jobhub.entity.job.BookingDispatchEvent;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobProviderAssignment;
import com.jobhub.repository.chat.ChatMessageRepository;
import com.jobhub.repository.chat.ChatReadStateRepository;
import com.jobhub.repository.job.BookingDispatchEventRepository;
import com.jobhub.repository.job.JobRepository;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatPersistenceTest {

    @Test
    void chatQueriesWorkAgainstTheRealSchema() {
        try (var factory = new Configuration()
                .addAnnotatedClass(ChatMessage.class).addAnnotatedClass(ChatReadState.class).addAnnotatedClass(com.jobhub.entity.chat.ChatAttachment.class)
                .addAnnotatedClass(Job.class).addAnnotatedClass(JobProviderAssignment.class)
                .addAnnotatedClass(com.jobhub.entity.job.BookingRequest.class)
                .addAnnotatedClass(BookingDispatchEvent.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var primary = job(em, 14L, "CONFIRMED");
            var team = job(em, 99L, "COMPLETED");
            var declined = job(em, 99L, "IN_PROGRESS");
            var pending = job(em, 14L, "AWAITING_PAYMENT");
            assignment(em, team.getJobId(), 14L, "ACCEPTED");
            assignment(em, declined.getJobId(), 14L, "DECLINED");

            var first = message(em, primary.getJobId(), 21L, "c-1");
            var second = message(em, primary.getJobId(), 30L, null);
            var third = message(em, primary.getJobId(), 30L, null);
            var event = new BookingDispatchEvent(); event.setUserId(21L); event.setJobId(primary.getJobId());
            event.setEventType("CHAT_MESSAGE_FROM_PROVIDER"); event.setCreatedAt(Instant.now()); em.persist(event);
            em.flush(); em.clear();

            var repositories = new JpaRepositoryFactory(em);
            var jobs = repositories.getRepository(JobRepository.class);
            var chatStatuses = Set.of("CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED");
            assertThat(jobs.findChatJobsForProvider(14L, chatStatuses, Set.of("CANCELLED", "REMOVED", "DECLINED"),
                    PageRequest.of(0, 50))).extracting(Job::getJobId)
                    .containsExactlyInAnyOrder(primary.getJobId(), team.getJobId())
                    .doesNotContain(declined.getJobId(), pending.getJobId());
            assertThat(jobs.findTop50ByCustomerIdAndJobStatusInOrderByUpdatedAtDesc(5L, chatStatuses)).hasSize(3);

            var messages = repositories.getRepository(ChatMessageRepository.class);
            assertThat(messages.findTop50ByJobIdOrderByMessageIdDesc(primary.getJobId())).extracting(ChatMessage::getMessageId)
                    .containsExactly(third.getMessageId(), second.getMessageId(), first.getMessageId());
            assertThat(messages.findTop50ByJobIdAndMessageIdGreaterThanOrderByMessageIdAsc(primary.getJobId(), first.getMessageId()))
                    .extracting(ChatMessage::getMessageId).containsExactly(second.getMessageId(), third.getMessageId());
            assertThat(messages.countByJobIdAndMessageIdGreaterThanAndSenderUserIdNot(primary.getJobId(), 0L, 21L)).isEqualTo(2);
            assertThat(messages.countBySenderUserIdAndCreatedAtAfter(30L, Instant.now().minusSeconds(60))).isEqualTo(2);
            assertThat(messages.findBySenderUserIdAndClientMessageId(21L, "c-1")).isPresent();
            assertThat(messages.existsByJobIdAndMessageIdLessThan(primary.getJobId(), first.getMessageId())).isFalse();

            var events = repositories.getRepository(BookingDispatchEventRepository.class);
            assertThat(events.existsByUserIdAndJobIdAndEventTypeAndCreatedAtAfter(21L, primary.getJobId(),
                    "CHAT_MESSAGE_FROM_PROVIDER", Instant.now().minusSeconds(30))).isTrue();
            assertThat(events.existsByUserIdAndJobIdAndEventTypeAndCreatedAtAfter(30L, primary.getJobId(),
                    "CHAT_MESSAGE_FROM_PROVIDER", Instant.now().minusSeconds(30))).isFalse();

            var read = new ChatReadState(); read.setJobId(primary.getJobId()); read.setUserId(21L);
            read.setLastReadMessageId(second.getMessageId()); read.setUpdatedAt(Instant.now()); em.persist(read);
            em.flush();
            assertThat(repositories.getRepository(ChatReadStateRepository.class)
                    .findByJobIdAndUserId(primary.getJobId(), 21L)).isPresent();

            // A retried send with the same clientMessageId cannot create a second row.
            message(em, primary.getJobId(), 21L, "c-2");
            assertThatThrownBy(() -> { message(em, primary.getJobId(), 21L, "c-2"); em.flush(); })
                    .isInstanceOf(jakarta.persistence.PersistenceException.class);
            em.getTransaction().rollback();
        }
    }

    private static Job job(jakarta.persistence.EntityManager em, Long providerId, String status) {
        var job = new Job(); job.setCustomerId(5L); job.setAssignedServiceProviderId(providerId); job.setJobStatus(status);
        em.persist(job);
        return job;
    }

    private static void assignment(jakarta.persistence.EntityManager em, Long jobId, Long providerId, String status) {
        var a = new JobProviderAssignment(); a.setJobId(jobId); a.setServiceProviderId(providerId); a.setStatus(status);
        em.persist(a);
    }

    private static ChatMessage message(jakarta.persistence.EntityManager em, Long jobId, Long sender, String clientId) {
        var m = new ChatMessage(); m.setJobId(jobId); m.setSenderUserId(sender); m.setSenderRole("CUSTOMER");
        m.setClientMessageId(clientId); m.setBodyCiphertext("v1:x"); m.setCreatedAt(Instant.now());
        em.persist(m);
        return m;
    }
}
