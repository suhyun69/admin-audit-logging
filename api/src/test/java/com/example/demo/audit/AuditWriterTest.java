package com.example.demo.audit;

import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.support.PostgresTestSupport;
import com.example.demo.usecase.AuditWriteUseCase;
import com.example.demo.usecase.IndependentAuditWriteUseCase;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AuditWriterTest extends PostgresTestSupport {
    @Autowired private AuditWriteUseCase atomic;
    @Autowired private IndependentAuditWriteUseCase independent;
    @Autowired private ObserveAuditUseCase observer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    private TransactionTemplate tx;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(manager);
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_success");
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs,admin_sessions,admin_users,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(email,name) VALUES ('member@example.test','Member')");
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('admin','hash','Admin','ADMIN')");
        var context = new RequestContext(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        context.setActor(new ActorSnapshot(1,"Admin","ADMIN",UUID.randomUUID()));
        RequestContext.install(context);
    }
    @AfterEach
    void cleanup() {
        RequestContext.clear();
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_success");
    }
    private AuditWriteAppRequest event(String result) {
        return AuditWriteAppRequest.event("DATA_UPDATE","USER_STATUS_CHANGE","BUSINESS",result)
                .resource("USER","1",Map.of("size",20,"password","secret","q","person@example.test"),
                        result.equals("SUCCESS") ? List.of(new AuditChangeAppRequest("status","ACTIVE","SUSPENDED")) : List.of())
                .http(new AuditHttpAppRequest("PATCH","/api/v1/users/1/status?password=secret",200,4L,"2001:db8::1","a".repeat(600),null));
    }
    @Test
    void successAndChangesCommitTogetherWithServerContextAndSanitizedMetadata() {
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE users SET status='SUSPENDED',version=version+1 WHERE id=1");
            atomic.record(event("SUCCESS"));
        });
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=1",String.class)).isEqualTo("SUSPENDED");
        var row = jdbc.queryForMap("SELECT actor_id,request_id,interaction_id,host(ip_address) ip,request_path,length(user_agent) agent_length,metadata::text metadata FROM audit_logs");
        assertThat(row.get("actor_id")).isEqualTo(1L);
        assertThat(row.get("request_id")).isEqualTo(RequestContext.current().orElseThrow().requestId());
        assertThat(row.get("interaction_id")).isEqualTo(RequestContext.current().orElseThrow().interactionId());
        assertThat(row.get("ip")).isEqualTo("2001:db8::1");
        assertThat(row.get("request_path")).isEqualTo("/api/v1/users/1/status");
        assertThat(row.get("agent_length")).isEqualTo(512);
        assertThat(row.get("metadata").toString()).contains("size").doesNotContain("secret","password","person@example.test");
        assertThat(jdbc.queryForObject("SELECT before_value #>> '{}' FROM audit_log_changes",String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT after_value #>> '{}' FROM audit_log_changes",String.class)).isEqualTo("SUSPENDED");
    }
    @Test
    void callerRollbackRollsBackBothDataAndAuditThenIndependentFailureCommits() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=1");
            atomic.record(event("SUCCESS"));
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);
        independent.record(event("FAILURE"));
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=1",String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE result='SUCCESS'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE result='FAILURE'",Integer.class)).isEqualTo(1);
    }
    @Test
    void auditInsertFailureRollsBackBusinessChange() {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_success CHECK (result <> 'SUCCESS')");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=1");
            atomic.record(event("SUCCESS"));
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=1",String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)).isZero();
    }
    @Test
    void independentWriterReallySuspendsOuterTransactionAndSurvivesRollback() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            independent.record(event("FAILURE"));
            throw new IllegalStateException("outer rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE result='FAILURE'",Integer.class)).isEqualTo(1);
    }
    @Test
    void observationFailureDoesNotPoisonCallerTransactionAndAtomicWriterRequiresTransaction() {
        assertThatThrownBy(() -> atomic.record(event("SUCCESS"))).isInstanceOf(IllegalTransactionStateException.class);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_success CHECK (result <> 'SUCCESS')");
        tx.executeWithoutResult(status -> {
            observer.record(event("SUCCESS"));
            jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=1");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=1",String.class)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)).isZero();
    }
    @Test
    void changesCannotContainPrivateFields() {
        var invalid = event("SUCCESS").resource("USER","1",Map.of(),List.of(new AuditChangeAppRequest("email","old@example.test","new@example.test")));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> atomic.record(invalid))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)).isZero();
    }
}
