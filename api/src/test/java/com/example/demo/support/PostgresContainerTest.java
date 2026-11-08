package com.example.demo.support;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class PostgresContainerTest extends PostgresTestSupport {
    @Test
    void connectsToPostgreSql17WithDockerClient() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database(), current_setting('server_version_num')::int")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("audit_test");
            assertThat(result.getInt(2)).isBetween(170000, 179999);
        }
    }
}
