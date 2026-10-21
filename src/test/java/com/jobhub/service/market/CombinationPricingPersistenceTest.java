package com.jobhub.service.market;

import com.jobhub.entity.job.*;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CombinationPricingPersistenceTest {
    @Test void rateConditionsAndBookingSnapshotsSurviveReload() {
        try (var factory = new Configuration().addAnnotatedClass(JobRate.class).addAnnotatedClass(Job.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var rate = new JobRate();
            rate.getConditions().add(new JobRateCondition(45L, "CAR"));
            rate.getConditions().add(new JobRateCondition(46L, "MALE"));
            em.persist(rate);
            var job = new Job(); job.setRateId(rate.getRateId());
            job.setPricingSelectionsJson("[{\"fieldId\":45,\"choiceValue\":\"CAR\"}]");
            job.setPricingSnapshotJson("{\"baseFare\":250,\"pricePerKm\":100}");
            em.persist(job); em.flush(); em.clear();
            assertThat(em.find(JobRate.class, rate.getRateId()).getConditions())
                    .extracting(JobRateCondition::getChoiceValue).containsExactlyInAnyOrder("CAR", "MALE");
            var restored = em.find(Job.class, job.getJobId());
            assertThat(restored.getPricingSelectionsJson()).isEqualTo(job.getPricingSelectionsJson());
            assertThat(restored.getPricingSnapshotJson()).isEqualTo(job.getPricingSnapshotJson());
            em.getTransaction().rollback();
        }
    }

    @Test void migrationPreservesExistingRowsAndAddsConditionStorage() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE job_rate (rate_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE service_custom_field (field_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE job (job_id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO job VALUES (1)"); sql.execute("INSERT INTO job_rate VALUES (71)");
            sql.execute("INSERT INTO service_custom_field VALUES (45), (46)");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/COMBINATION_PRICING_SCHEMA.sql"));
            sql.execute("INSERT INTO job_rate_condition VALUES (71,45,'CAR'), (71,46,'MALE')");
            try (var rows = sql.executeQuery("SELECT pricing_selections_json, pricing_snapshot_json FROM job WHERE job_id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull(); assertThat(rows.getString(2)).isNull();
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO job_rate_condition VALUES (71,45,'BIKE')"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
