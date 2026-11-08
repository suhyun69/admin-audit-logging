package com.example.demo.migration;

import com.example.demo.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BusinessSchemaTest extends PostgresTestSupport {
    private JdbcTemplate jdbc;
    private Flyway flyway;

    @BeforeEach
    void migrate() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        flyway = Flyway.configure().dataSource(ds).target("1").load();
        flyway.migrate();
        jdbc.execute("TRUNCATE admin_sessions, admin_users, users RESTART IDENTITY CASCADE");
    }

    @Test
    void migrationIsRepeatableWithoutChangingInstalledSchema() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN ('idx_admin_sessions_user','idx_admin_sessions_expires','idx_users_status_id')", Integer.class)).isEqualTo(3);
    }

    @Test
    void businessDefaultsAndTimeZoneColumnsRoundTrip() {
        jdbc.update("INSERT INTO users(email,name) VALUES ('member@example.test','Member')");
        var row = jdbc.queryForMap("SELECT status, version, created_at, updated_at FROM users");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("version")).isEqualTo(0L);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void rejectsInvalidRoleStatusDuplicateIdentityAndUnknownSessionOwner() {
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('admin','hash','Admin','ADMIN')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('bad','hash','Bad','ROOT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('admin','hash','Duplicate','VIEWER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users(email,name,status) VALUES ('bad@example.test','Bad','DELETED')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO users(email,name) VALUES ('member@example.test','Member')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users(email,name) VALUES ('member@example.test','Duplicate')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO admin_sessions(id,admin_user_id,token_hash,expires_at) VALUES (?,999,'hash',now()+interval '1 hour')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionTokenHashIsUnique() {
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('admin','hash','Admin','ADMIN')");
        jdbc.update("INSERT INTO admin_sessions(id,admin_user_id,token_hash,expires_at) VALUES (?,1,'same-hash',now()+interval '1 hour')", UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO admin_sessions(id,admin_user_id,token_hash,expires_at) VALUES (?,1,'same-hash',now()+interval '1 hour')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
