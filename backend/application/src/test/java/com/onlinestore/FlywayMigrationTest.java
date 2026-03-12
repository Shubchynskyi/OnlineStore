package com.onlinestore;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

    @Test
    void migrationScriptsShouldExist() {
        URL v1 = getClass().getResource("/db/migration/V1__init_schema.sql");
        URL v2 = getClass().getResource("/db/migration/V2__seed_data.sql");
        URL v6 = getClass().getResource("/db/migration/V6__cart_domain.sql");
        assertThat(v1).isNotNull();
        assertThat(v2).isNotNull();
        assertThat(v6).isNotNull();
    }
}
