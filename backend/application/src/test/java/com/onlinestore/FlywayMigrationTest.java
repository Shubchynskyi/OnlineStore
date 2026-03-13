package com.onlinestore;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    private static final List<String> EXPECTED_MIGRATION_VERSIONS = List.of("1", "2", "3", "4", "5", "6");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("online_store_flyway_test")
        .withUsername("test")
        .withPassword("test");

    @Test
    void migrationsShouldApplyToPostgresqlAndProvisionExpectedArtifacts() throws SQLException {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();

        flyway.migrate();

        assertThat(appliedMigrationVersions(flyway)).containsExactlyElementsOf(EXPECTED_MIGRATION_VERSIONS);
        assertThat(countQuery("SELECT COUNT(*) FROM categories")).isEqualTo(4);
        assertThat(stringListQuery("SELECT slug FROM categories ORDER BY sort_order"))
            .containsExactly("electronics", "clothing", "home-garden", "sports");
        assertThat(countQuery("SELECT COUNT(*) FROM payment_provider_configs")).isEqualTo(4);
        assertThat(extensionExists("uuid-ossp")).isTrue();
        assertThat(extensionExists("pg_trgm")).isTrue();
        assertThat(tableExists("payment_webhook_events")).isTrue();
        assertThat(tableExists("outbox_events")).isTrue();
        assertThat(tableExists("carts")).isTrue();
        assertThat(indexExists("ux_payment_webhook_events_provider_event")).isTrue();
        assertThat(indexExists("idx_outbox_events_status_next_attempt")).isTrue();
        assertThat(indexExists("idx_cart_items_product_variant_id")).isTrue();
    }

    private List<String> appliedMigrationVersions(Flyway flyway) {
        return Arrays.stream(flyway.info().applied())
            .filter(Objects::nonNull)
            .map(MigrationInfo::getVersion)
            .filter(Objects::nonNull)
            .map(version -> version.getVersion())
            .toList();
    }

    private int countQuery(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private List<String> stringListQuery(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<String> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
            return results;
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        return booleanQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = '%s')".formatted(tableName)
        );
    }

    private boolean indexExists(String indexName) throws SQLException {
        return booleanQuery(
            "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '%s')"
                .formatted(indexName)
        );
    }

    private boolean extensionExists(String extensionName) throws SQLException {
        return booleanQuery(
            "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = '%s')".formatted(extensionName)
        );
    }

    private boolean booleanQuery(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
