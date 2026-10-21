package com.jobhub.service.provider;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SharedServiceOptionMigrationTest {
    @Test void preservesExistingMembershipsAndInactiveStatusOnRepeatedMigration() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE service_provider_type (service_provider_type_id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO service_provider_type VALUES (1)");
            sql.execute("CREATE TABLE service_option (option_id BIGINT PRIMARY KEY, service_provider_type_id BIGINT, display_order INT, status VARCHAR(20))");
            sql.execute("INSERT INTO service_option VALUES (12, 1, 8, 'INACTIVE'), (13, 1, NULL, 'ACTIVE')");
            var migration = new FileSystemResource("docs/SHARED_SERVICE_OPTIONS_SCHEMA.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            try (var rows = sql.executeQuery("SELECT option_id, display_order, status FROM service_type_option ORDER BY option_id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(12);
                assertThat(rows.getInt(2)).isEqualTo(8);
                assertThat(rows.getString(3)).isEqualTo("INACTIVE");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(13);
                assertThat(rows.getInt(2)).isZero();
                assertThat(rows.getString(3)).isEqualTo("ACTIVE");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
