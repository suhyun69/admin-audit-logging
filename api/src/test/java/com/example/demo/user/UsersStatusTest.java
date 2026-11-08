package com.example.demo.user;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UsersStatusTest extends HttpApiTestSupport {
    @BeforeEach void reset() {
        cleanupDatabaseFaults();
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");
        jdbc.update("UPDATE users SET status='ACTIVE',version=0,updated_at=now() WHERE id=123");
    }
    @AfterEach void cleanup() { cleanupDatabaseFaults(); }
    private void cleanupDatabaseFaults() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_user_business_failure ON users");
        jdbc.execute("DROP FUNCTION IF EXISTS test_user_business_failure()");
        jdbc.execute("DROP TRIGGER IF EXISTS test_user_race_lock ON users");
        jdbc.execute("DROP FUNCTION IF EXISTS test_user_race_lock()");
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_update_success");
    }
    private MvcResult change(Session session,String id,String json,UUID interaction) throws Exception {
        return mvc.perform(patch("/api/v1/users/"+id+"/status").cookie(session.auth()).session(session.csrfSession())
                .header("X-CSRF-TOKEN",session.csrfToken()).header("X-Interaction-ID",interaction.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }
    private int businessCount(MvcResult result,String outcome) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='BUSINESS' AND result=?",Integer.class,requestId(result),outcome);
    }
    @ParameterizedTest @ValueSource(strings={"admin","operator"})
    void authorizedChangeCommitsStatusVersionAndExactBeforeAfter(String username) throws Exception {
        var actor=login(username);UUID interaction=UUID.randomUUID();
        var result=change(actor,"123","{\"status\":\"SUSPENDED\",\"version\":0}",interaction);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body=mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("previousStatus").asText()).isEqualTo("ACTIVE");assertThat(body.get("currentStatus").asText()).isEqualTo("SUSPENDED");assertThat(body.get("version").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=123",String.class)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=123",Long.class)).isEqualTo(1L);
        assertThat(businessCount(result,"SUCCESS")).isEqualTo(1);
        var row=jdbc.queryForMap("SELECT c.field_name,c.before_value #>> '{}' before,c.after_value #>> '{}' after,a.interaction_id FROM audit_log_changes c JOIN audit_logs a ON a.id=c.audit_log_id WHERE a.request_id=?",requestId(result));
        assertThat(row.get("field_name")).isEqualTo("status");assertThat(row.get("before")).isEqualTo("ACTIVE");assertThat(row.get("after")).isEqualTo("SUSPENDED");assertThat(row.get("interaction_id")).isEqualTo(interaction);
    }
    @Test void noOpDoesNotWriteAndStaleVersionIsRejectedBeforeNoOp() throws Exception {
        var actor=login("admin");var before=jdbc.queryForObject("SELECT updated_at FROM users WHERE id=123",java.sql.Timestamp.class);
        var unchanged=change(actor,"123","{\"status\":\"ACTIVE\",\"version\":0}",UUID.randomUUID());
        assertThat(unchanged.getResponse().getStatus()).isEqualTo(200);
        assertThat(businessCount(unchanged,"SUCCESS")).isZero();
        assertThat(jdbc.queryForObject("SELECT updated_at FROM users WHERE id=123",java.sql.Timestamp.class)).isEqualTo(before);
        var changed=change(actor,"123","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());assertThat(changed.getResponse().getStatus()).withFailMessage(changed.getResponse().getContentAsString()).isEqualTo(200);
        var stale=change(actor,"123","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());
        assertThat(stale.getResponse().getStatus()).isEqualTo(409);assertThat(mapper.readTree(stale.getResponse().getContentAsString()).get("code").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(businessCount(stale,"FAILURE")).isEqualTo(1);
    }
    @Test void viewerCannotChangeDataAndOnlySecurityDenialIsRecorded() throws Exception {
        var result=change(login("viewer"),"123","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=123",String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=123",Long.class)).isZero();
        assertThat(businessCount(result,"FAILURE")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND event_type='AUTH_ACCESS_DENIED'",Integer.class,requestId(result))).isEqualTo(1);
    }
    @Test void businessDatabaseExceptionRollsBackAndFailureAuditCommitsAfterward() throws Exception {
        jdbc.execute("CREATE FUNCTION test_user_business_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test business failure'; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_user_business_failure AFTER UPDATE ON users FOR EACH ROW EXECUTE FUNCTION test_user_business_failure()");
        var result=change(login("admin"),"123","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());
        assertThat(result.getResponse().getStatus()).isEqualTo(500);assertThat(businessCount(result,"FAILURE")).isEqualTo(1);assertThat(businessCount(result,"SUCCESS")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=123",String.class)).isEqualTo("ACTIVE");assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=123",Long.class)).isZero();
    }
    @Test void auditInsertFailureRollsBackActualBusinessUpdateAndChanges() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_update_success CHECK (NOT(event_type='DATA_UPDATE' AND result='SUCCESS'))");
        var result=change(login("admin"),"123","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());
        assertThat(result.getResponse().getStatus()).isEqualTo(500);assertThat(businessCount(result,"FAILURE")).isEqualTo(1);assertThat(businessCount(result,"SUCCESS")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=123",String.class)).isEqualTo("ACTIVE");assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=123",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes c JOIN audit_logs a ON a.id=c.audit_log_id WHERE a.request_id=?",Integer.class,requestId(result))).isZero();
    }
    @Test void twoConcurrentRequestsThatReadVersionZeroProduceOneSuccessAndOneConflict() throws Exception {
        var actor=login("admin");
        jdbc.execute("CREATE FUNCTION test_user_race_lock() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock(901101); RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_user_race_lock BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION test_user_race_lock()");
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try(var lock=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var statement=lock.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(901101)");
            var tasks=new java.util.ArrayList<Future<MvcResult>>();
            for(int n=0;n<2;n++)tasks.add(executor.submit(()->{ready.countDown();start.await();return change(actor,"123","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);int waiting=0;
            try {
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE 'UPDATE users SET status%'",Integer.class);
                    if(waiting==2)break;Thread.sleep(20);
                }
                assertThat(waiting).as("both UPDATE statements reached the database before releasing the lock").isEqualTo(2);
            } finally { statement.execute("SELECT pg_advisory_unlock(901101)"); }
            var first=tasks.get(0).get(10,TimeUnit.SECONDS);var second=tasks.get(1).get(10,TimeUnit.SECONDS);
            assertThat(java.util.List.of(first.getResponse().getStatus(),second.getResponse().getStatus())).containsExactlyInAnyOrder(200,409);
            assertThat(businessCount(first,"SUCCESS")+businessCount(second,"SUCCESS")).isEqualTo(1);assertThat(businessCount(first,"FAILURE")+businessCount(second,"FAILURE")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id=123",Long.class)).isEqualTo(1L);
        } finally { start.countDown();executor.shutdownNow(); }
    }
    @Test void missingMemberIs404AndVersionMustBePresentNonNegativeInteger() throws Exception {
        var actor=login("admin");var missing=change(actor,"999999","{\"status\":\"SUSPENDED\",\"version\":0}",UUID.randomUUID());
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);assertThat(businessCount(missing,"FAILURE")).isEqualTo(1);
        for(String body:new String[]{"{\"status\":\"DELETED\",\"version\":0}","{\"status\":\"SUSPENDED\"}","{\"status\":\"SUSPENDED\",\"version\":-1}","{\"status\":\"SUSPENDED\",\"version\":0.5}","{\"status\":\"SUSPENDED\",\"version\":\"0\"}"}) {
            var result=change(actor,"123",body,UUID.randomUUID());assertThat(result.getResponse().getStatus()).isEqualTo(400);assertThat(businessCount(result,"FAILURE")).isZero();
        }
    }
}
