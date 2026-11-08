package com.example.demo.migration;

import com.example.demo.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuditSchemaTest extends PostgresTestSupport {
    private JdbcTemplate jdbc;
    @BeforeEach
    void migrate() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("2").load().migrate();
        jdbc.execute("TRUNCATE audit_log_changes, audit_logs, admin_sessions, admin_users RESTART IDENTITY CASCADE");
    }

    @Test
    void postgresJsonInetUuidAndChangesRoundTrip() {
        UUID id = UUID.randomUUID(), interaction = UUID.randomUUID(), request = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs(id,event_type,action,source,result,interaction_id,request_id,ip_address,metadata) VALUES (?,'DATA_UPDATE','USER_STATUS_CHANGE','BUSINESS','SUCCESS',?,?,CAST(? AS inet),CAST(? AS jsonb))",
                id, interaction, request, "2001:db8::1", "{\"returnedCount\":3}");
        jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (?,'status','\"ACTIVE\"'::jsonb,'\"SUSPENDED\"'::jsonb)", id);
        var row = jdbc.queryForMap("SELECT id, interaction_id, request_id, host(ip_address) ip, metadata->>'returnedCount' count FROM audit_logs");
        assertThat(row.get("id")).isEqualTo(id);
        assertThat(row.get("interaction_id")).isEqualTo(interaction);
        assertThat(row.get("request_id")).isEqualTo(request);
        assertThat(row.get("ip")).isEqualTo("2001:db8::1");
        assertThat(row.get("count")).isEqualTo("3");
        assertThat(jdbc.queryForObject("SELECT after_value #>> '{}' FROM audit_log_changes", String.class)).isEqualTo("SUSPENDED");
    }

    @Test
    void rejectsDuplicateEventIdInvalidSourceResultAndUnknownReferences() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs(id,event_type,action,source,result) VALUES (?,'PAGE_VIEW','PAGE_ENTER','CLIENT','ATTEMPTED')", id);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs(id,event_type,action,source,result) VALUES (?,'PAGE_VIEW','PAGE_ENTER','CLIENT','ATTEMPTED')", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs(id,event_type,action,source,result) VALUES (?,'PAGE_VIEW','PAGE_ENTER','FORGED','ATTEMPTED')", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs(id,event_type,action,source,result) VALUES (?,'PAGE_VIEW','PAGE_ENTER','CLIENT','FORGED')", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs(id,event_type,action,source,result,actor_id) VALUES (?,'PAGE_VIEW','PAGE_ENTER','CLIENT','ATTEMPTED',999)", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name) VALUES (?,'status')", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationAddsAllAuditIndexesAndDoesNotRerun() {
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("2").load();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN ('idx_audit_time','idx_audit_actor_time','idx_audit_type_time','idx_audit_interaction','idx_audit_request','idx_audit_changes')", Integer.class)).isEqualTo(6);
    }
}
