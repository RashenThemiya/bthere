package com.jobhub.service.market;

import com.jobhub.dto.market.PlatformFeeRequest;
import com.jobhub.entity.job.*;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.audit.AuditService;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlatformFeePersistenceTest {
    @Test void configurationPersistsButLaterEditsDoNotAlterSavedBookingSplit() {
        try(var factory=new Configuration().addAnnotatedClass(ServiceProviderType.class).addAnnotatedClass(ServiceOption.class)
                .addAnnotatedClass(JobRate.class).addAnnotatedClass(Job.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class","org.h2.Driver")
                .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto","create-drop").buildSessionFactory();var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            var type=new ServiceProviderType();type.setName("Ride");em.persist(type);
            var option=new ServiceOption();option.setCode("CAR");option.setServiceProviderTypeId(type.getServiceProviderTypeId());em.persist(option);em.flush();
            var repos=new JpaRepositoryFactory(em);
            var fees=new PlatformFeeService(repos.getRepository(ServiceProviderTypeRepository.class),repos.getRepository(ServiceOptionRepository.class),mock(AuditService.class));
            fees.updateService(1L,type.getServiceProviderTypeId(),new PlatformFeeRequest(new BigDecimal("15")));
            var split=fees.resolve(new JobRate(),type.getServiceProviderTypeId(),option.getOptionId(),new BigDecimal("1000.00"));
            var booking=new Job();booking.setExpectedAmount(new BigDecimal("1000"));booking.setPlatformFeePercentage(split.percentage());
            booking.setPlatformFee(split.platformFee());booking.setProviderEarnings(split.providerEarnings());em.persist(booking);em.flush();
            Long jobId=booking.getJobId(),optionId=option.getOptionId();em.clear();
            fees.updateOption(1L,optionId,new PlatformFeeRequest(new BigDecimal("90")));em.flush();em.clear();
            assertThat(fees.optionConfiguration(optionId).effectivePlatformFeePercentage()).isEqualByComparingTo("90");
            var saved=em.find(Job.class,jobId);assertThat(saved.getPlatformFeePercentage()).isEqualByComparingTo("15");
            assertThat(saved.getPlatformFee()).isEqualByComparingTo("150");assertThat(saved.getProviderEarnings()).isEqualByComparingTo("850");
            em.getTransaction().rollback();
        }
    }
    @Test void migrationAddsNullableOverridesWithoutRepricingHistoricalBookings() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL")){
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE service_provider_type (service_provider_type_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE service_option (option_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE job_rate (rate_id BIGINT PRIMARY KEY,rate DECIMAL(38,2))");
            sql.execute("CREATE TABLE job (job_id BIGINT PRIMARY KEY,expected_amount DECIMAL(38,2),platform_fee DECIMAL(38,2))");
            sql.execute("CREATE TABLE booking_request (request_id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO job VALUES (1,1000,125)");sql.execute("INSERT INTO job_rate VALUES (1,1000)");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("docs/PLATFORM_FEES_SCHEMA.sql"));
            try(var row=sql.executeQuery("SELECT * FROM job WHERE job_id=1")){
                assertThat(row.next()).isTrue();assertThat(row.getBigDecimal("expected_amount")).isEqualByComparingTo("1000");
                assertThat(row.getBigDecimal("platform_fee")).isEqualByComparingTo("125");
                assertThat(row.getBigDecimal("platform_fee_percentage")).isNull();assertThat(row.getBigDecimal("provider_earnings")).isNull();
            }
            try(var row=sql.executeQuery("SELECT platform_fee_percentage FROM job_rate WHERE rate_id=1")){
                assertThat(row.next()).isTrue();assertThat(row.getBigDecimal(1)).isNull();
            }
        }
    }
}
