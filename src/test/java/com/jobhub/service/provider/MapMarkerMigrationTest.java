package com.jobhub.service.provider;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MapMarkerMigrationTest {
    @Test void migrationPreservesExistingMembershipAndDefaultsSelectorToNull() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE service_type_option (option_id BIGINT, service_provider_type_id BIGINT, status VARCHAR(20))");
            sql.execute("INSERT INTO service_type_option VALUES (12,1,'ACTIVE')");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/MAP_MARKER_ICONS_SCHEMA.sql"));
            try (var rows = sql.executeQuery("SELECT option_id, status, map_marker_field_id FROM service_type_option")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(12);
                assertThat(rows.getString(2)).isEqualTo("ACTIVE");
                assertThat(rows.getObject(3)).isNull();
            }
        }
    }
}
