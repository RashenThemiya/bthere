package com.jobhub.service.provider;

import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.ServiceOptionMapMarkerRuleRepository;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MapMarkerRulePersistenceTest {
    @Test void repositoryLoadsOnlyRequestedServiceRulesAndDeleteRemovesConditions() {
        try (var factory = new Configuration().addAnnotatedClass(ServiceOptionMapMarkerRule.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
             var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var rule = new ServiceOptionMapMarkerRule(); rule.setServiceProviderTypeId(1L); rule.setOptionId(12L);
            var icon = new ServiceMediaValue(); icon.setMediaType("MAP_MARKER"); icon.setObjectKey("car-ev.png");
            icon.setUrl("https://example.com/car-ev.png"); icon.setDisplayOrder(0); rule.setMarkerIcon(icon);
            rule.getConditions().add(new MapMarkerCondition(45L,"CAR"));
            rule.getConditions().add(new MapMarkerCondition(47L,"ELECTRIC"));
            em.persist(rule); em.flush(); em.clear();
            var repository = new JpaRepositoryFactory(em).getRepository(ServiceOptionMapMarkerRuleRepository.class);
            assertThat(repository.findAllByServiceProviderTypeIdAndOptionIdAndStatus(2L,12L,"ACTIVE")).isEmpty();
            var loaded = repository.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L,12L,"ACTIVE");
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getConditions()).extracting(MapMarkerCondition::getChoiceValue)
                    .containsExactlyInAnyOrder("CAR","ELECTRIC");
            assertThat(loaded.get(0).getMarkerIcon().getObjectKey()).isEqualTo("car-ev.png");
            loaded.get(0).setStatus("INACTIVE"); em.flush(); em.clear();
            assertThat(repository.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L,12L,"ACTIVE")).isEmpty();
            var all = repository.findAllByServiceProviderTypeIdAndOptionIdOrderByMarkerRuleIdAsc(1L,12L);
            assertThat(all).hasSize(1);
            repository.delete(all.get(0)); em.flush();
            assertThat(((Number)em.createNativeQuery("select count(*) from service_option_map_marker_condition").getSingleResult()).longValue()).isZero();
            em.getTransaction().rollback();
        }
    }

    @Test void migrationCreatesRuleAndConditionStorageAndCanBeRerun() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE service_provider_type (service_provider_type_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE service_option (option_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE service_custom_field (field_id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO service_provider_type VALUES (1)");
            sql.execute("INSERT INTO service_option VALUES (12)");
            sql.execute("INSERT INTO service_custom_field VALUES (45),(47)");
            var migration = new FileSystemResource("docs/COMBINATION_MAP_MARKERS_SCHEMA.sql");
            ScriptUtils.executeSqlScript(connection,migration);
            sql.execute("INSERT INTO service_option_map_marker_rule (marker_rule_id,service_provider_type_id,option_id,status,media_type,object_key,url,display_order,created_at,updated_at) VALUES (7,1,12,'ACTIVE','MAP_MARKER','car.png','https://example.com/car.png',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO service_option_map_marker_condition VALUES (7,45,'CAR'),(7,47,'ELECTRIC')");
            ScriptUtils.executeSqlScript(connection,migration);
            try (var rows = sql.executeQuery("SELECT count(*) FROM service_option_map_marker_condition WHERE marker_rule_id=7")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO service_option_map_marker_condition VALUES (7,45,'BIKE')"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
