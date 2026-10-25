package com.jobhub.service.customer;

import com.jobhub.entity.job.*;
import com.jobhub.repository.job.*;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BookingRequestPersistenceTest {
    @Test void durableQueueQueriesFindExpiredOffersWithoutBlockingBehindFutureRequests() {
        try(var factory=new Configuration().addAnnotatedClass(BookingRequest.class).addAnnotatedClass(BookingOffer.class)
                .addAnnotatedClass(BookingRequestPolicy.class).addAnnotatedClass(BookingDispatchEvent.class).addAnnotatedClass(BookingPaymentNotice.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class","org.h2.Driver")
                .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto","create-drop").buildSessionFactory();var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            var early=new BookingRequest();early.setUserId(5L);early.setIdempotencyKey("a");early.setStatus("MATCHING");early.setExpiresAt(Instant.now().plusSeconds(300));em.persist(early);
            var due=new BookingRequest();due.setUserId(5L);due.setIdempotencyKey("b");due.setStatus("MATCHING");due.setExpiresAt(Instant.now().plusSeconds(300));em.persist(due);
            var offer=new BookingOffer();offer.setRequestId(due.getRequestId());offer.setProviderId(15L);offer.setStatus("PENDING");offer.setExpiresAt(Instant.now().minusSeconds(30));em.persist(offer);
            var event=new BookingDispatchEvent();event.setUserId(9L);event.setRequestId(due.getRequestId());event.setNextDeliveryAt(Instant.now().minusSeconds(1));event.setCreatedAt(Instant.now());em.persist(event);
            em.flush();em.clear();
            var repositories=new JpaRepositoryFactory(em);var requests=repositories.getRepository(BookingRequestRepository.class);
            assertThat(requests.findDue(Instant.now(),Instant.now().minusSeconds(900),PageRequest.of(0,1)))
                    .extracting(BookingRequest::getRequestId).containsExactly(due.getRequestId());
            assertThat(requests.findByUserIdAndIdempotencyKey(5L,"a")).isPresent();assertThat(requests.lockById(due.getRequestId())).isPresent();
            var events=repositories.getRepository(BookingDispatchEventRepository.class);
            assertThat(events.findTop100ByUserIdAndEventIdGreaterThanOrderByEventIdAsc(9L,0L)).hasSize(1);
            assertThat(events.findTop100ByUserIdAndEventIdGreaterThanOrderByEventIdAsc(5L,0L)).isEmpty();
            assertThat(events.findTop10ByDeliveryStatusAndNextDeliveryAtBeforeOrderByEventIdAsc("PENDING",Instant.now())).hasSize(1);
            em.getTransaction().rollback();
        }
    }
    @Test void migrationCanBeAppliedTwiceAndEnforcesIdempotencyAndCallbackUniqueness() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL")){
            var migration=new FileSystemResource("docs/BOOKING_REQUESTS_SCHEMA.sql");
            ScriptUtils.executeSqlScript(connection,migration);ScriptUtils.executeSqlScript(connection,migration);
            var sql=connection.createStatement();
            sql.execute("INSERT INTO booking_request(request_id,user_id,idempotency_key,created_at,updated_at) VALUES(1,5,'same',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> sql.execute("INSERT INTO booking_request(user_id,idempotency_key,created_at,updated_at) VALUES(5,'same',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"))
                    .isInstanceOf(java.sql.SQLException.class);
            sql.execute("INSERT INTO booking_payment_notice(request_id,payment_id,status_code) VALUES(1,'payment-1','2')");
            assertThatThrownBy(() -> sql.execute("INSERT INTO booking_payment_notice(request_id,payment_id,status_code) VALUES(1,'payment-1','2')"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
