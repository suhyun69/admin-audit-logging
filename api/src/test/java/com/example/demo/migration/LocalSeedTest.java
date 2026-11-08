package com.example.demo.migration;

import com.example.demo.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LocalSeedTest extends PostgresTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    @Test
    void localApplicationBootsWithBcryptAccountsAndSafeMemberSequence() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_users", Integer.class)).isEqualTo(3);
        var encoder = new BCryptPasswordEncoder();
        for (var row : jdbc.queryForList("SELECT username, password_hash, role FROM admin_users")) {
            assertThat(row.get("username")).isEqualTo(row.get("role").toString().toLowerCase(java.util.Locale.ROOT));
            assertThat(encoder.matches("local-test-password", row.get("password_hash").toString())).isTrue();
            assertThat(row.get("password_hash")).isNotEqualTo("local-test-password");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=123", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("INSERT INTO users(email,name) VALUES ('next@example.test','Next') RETURNING id", Long.class)).isEqualTo(201L);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_users", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(201);
    }
}
