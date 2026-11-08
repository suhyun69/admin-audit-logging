package com.example.demo.qa;

import com.example.demo.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Independent actual-HTTP-boundary transaction contracts; no rollback-only test transaction or production hook. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class MemberTransactionContractTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    record Actor(Cookie cookie, MockHttpSession session, String csrf) {}

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(POSTGRES.getDatabaseName());
        assertThat(POSTGRES.getDatabaseName()).isEqualTo("audit_test");
        cleanupFaults();
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs,admin_sessions,admin_users,users RESTART IDENTITY CASCADE");
        String hash = new BCryptPasswordEncoder().encode("transaction-contract-password");
        for (String role : new String[]{"ADMIN", "OPERATOR", "VIEWER"})
            jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                    role.toLowerCase(java.util.Locale.ROOT), hash, "QA " + role, role);
        jdbc.update("INSERT INTO users(id,email,name) OVERRIDING SYSTEM VALUE VALUES (123,'member123@example.test','Contract member')");
    }

    @AfterEach
    void cleanupFaults() {
        jdbc.execute("DROP TRIGGER IF EXISTS qa_member_failure ON users");
        jdbc.execute("DROP FUNCTION IF EXISTS qa_member_failure()");
        jdbc.execute("DROP TRIGGER IF EXISTS qa_audit_failure ON audit_logs");
        jdbc.execute("DROP FUNCTION IF EXISTS qa_audit_failure()");
    }

    private Actor login(String username) throws Exception {
        var bootstrap = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        String csrf = json.readTree(bootstrap.getResponse().getContentAsString()).get("token").asText();
        var result = mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "transaction-contract-password"))))
                .andExpect(status().isOk()).andReturn();
        String header = result.getResponse().getHeaders("Set-Cookie").stream().filter(value -> value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        Cookie cookie = new Cookie("AUDIT_SESSION", header.substring("AUDIT_SESSION=".length(), header.indexOf(';')));
        var fresh = mvc.perform(get("/api/v1/auth/csrf").session(session).cookie(cookie)).andExpect(status().isOk()).andReturn();
        return new Actor(cookie, session, json.readTree(fresh.getResponse().getContentAsString()).get("token").asText());
    }

    private MvcResult change(Actor actor, String target, long version, UUID interaction) throws Exception {
        return mvc.perform(patch("/api/v1/users/123/status").cookie(actor.cookie()).session(actor.session())
                .header("X-CSRF-TOKEN", actor.csrf()).header("X-Interaction-ID", interaction.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("status", target, "version", version))))
                .andReturn();
    }

    private UUID requestId(MvcResult response) { return UUID.fromString(response.getResponse().getHeader("X-Request-ID")); }
    private int outcome(MvcResult response, String result) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND event_type='DATA_UPDATE' AND result=?", Integer.class, requestId(response), result);
    }
    private void state(String status, long version) {
        var row = jdbc.queryForMap("SELECT status,version FROM users WHERE id=123");
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(row.get("version")).isEqualTo(version);
    }
    private void failureRolledBack(MvcResult response) {
        assertThat(response.getResponse().getStatus()).isEqualTo(500);
        state("ACTIVE", 0);
        assertThat(outcome(response, "SUCCESS")).isZero();
        assertThat(outcome(response, "FAILURE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes c JOIN audit_logs a ON c.audit_log_id=a.id WHERE a.request_id=?", Integer.class, requestId(response))).isZero();
    }

    @Test
    void adminAndOperatorChangesCommitExactlyTheActualBeforeAndAfterValues() throws Exception {
        for (String username : new String[]{"admin", "operator"}) {
            Actor actor = login(username);
            String target = username.equals("admin") ? "SUSPENDED" : "ACTIVE";
            String before = username.equals("admin") ? "ACTIVE" : "SUSPENDED";
            long version = username.equals("admin") ? 0 : 1;
            UUID interaction = UUID.randomUUID();
            var result = change(actor, target, version, interaction);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            state(target, version + 1);
            assertThat(outcome(result, "SUCCESS")).isEqualTo(1);
            assertThat(outcome(result, "FAILURE")).isZero();
            var row = jdbc.queryForMap("SELECT actor_id,interaction_id,field_name,before_value #>> '{}' before,after_value #>> '{}' after FROM audit_logs a JOIN audit_log_changes c ON c.audit_log_id=a.id WHERE request_id=?", requestId(result));
            assertThat(row.get("actor_id")).isEqualTo(username.equals("admin") ? 1L : 2L);
            assertThat(row.get("interaction_id")).isEqualTo(interaction);
            assertThat(row.get("field_name")).isEqualTo("status");
            assertThat(row.get("before")).isEqualTo(before);
            assertThat(row.get("after")).isEqualTo(target);
        }
    }

    @Test
    void afterUpdateBusinessFailureRollsBackAndFailureAuditPersistsOutsideThatTransaction() throws Exception {
        jdbc.execute("CREATE FUNCTION qa_member_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'qa transactional failure'; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER qa_member_failure AFTER UPDATE ON users FOR EACH ROW EXECUTE FUNCTION qa_member_failure()");
        failureRolledBack(change(login("admin"), "SUSPENDED", 0, UUID.randomUUID()));
    }

    @Test
    void failureAfterSuccessAuditInsertAlsoRollsBackMemberAndChangesButAllowsFailureAudit() throws Exception {
        jdbc.execute("CREATE FUNCTION qa_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='DATA_UPDATE' AND NEW.result='SUCCESS' THEN RAISE EXCEPTION 'qa success audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER qa_audit_failure AFTER INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION qa_audit_failure()");
        failureRolledBack(change(login("admin"), "SUSPENDED", 0, UUID.randomUUID()));
    }

    @Test
    void twoRealUpdatesBlockedOnOneRowResolveToOneCommitAndOneVersionConflict() throws Exception {
        Actor actor = login("admin");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try (var lock = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            lock.setAutoCommit(false);
            try (var statement = lock.createStatement()) { statement.executeQuery("SELECT id FROM users WHERE id=123 FOR UPDATE").close(); }
            var tasks = new java.util.ArrayList<Future<MvcResult>>();
            for (int n = 0; n < 2; n++) tasks.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timeout");
                return change(actor, "SUSPENDED", 0, UUID.randomUUID());
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int waiting = 0;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            try {
                while (System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE 'UPDATE users SET status%'", Integer.class);
                    if (waiting == 2) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("Both actual UPDATE statements must reach the held row before releasing it").isEqualTo(2);
            } finally { lock.commit(); }
            var first = tasks.get(0).get(10, TimeUnit.SECONDS);
            var second = tasks.get(1).get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.getResponse().getStatus(), second.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
            state("SUSPENDED", 1);
            assertThat(outcome(first, "SUCCESS") + outcome(second, "SUCCESS")).isEqualTo(1);
            assertThat(outcome(first, "FAILURE") + outcome(second, "FAILURE")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes", Integer.class)).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void viewerCannotChangeMemberAndDoesNotCreateBusinessFailureOrChanges() throws Exception {
        var result = change(login("viewer"), "SUSPENDED", 0, UUID.randomUUID());
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        state("ACTIVE", 0);
        assertThat(outcome(result, "SUCCESS") + outcome(result, "FAILURE")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND event_type='AUTH_ACCESS_DENIED'", Integer.class, requestId(result))).isEqualTo(1);
    }

    @Test
    void sameStateKeepsVersionAndTimestampButStaleVersionIsRejectedBeforeNoOp() throws Exception {
        Actor actor = login("admin");
        var updated = jdbc.queryForObject("SELECT updated_at FROM users WHERE id=123", java.sql.Timestamp.class);
        for (int n = 0; n < 3; n++) {
            var same = change(actor, "ACTIVE", 0, UUID.randomUUID());
            assertThat(same.getResponse().getStatus()).isEqualTo(200);
            assertThat(outcome(same, "SUCCESS") + outcome(same, "FAILURE")).isZero();
        }
        state("ACTIVE", 0);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM users WHERE id=123", java.sql.Timestamp.class)).isEqualTo(updated);
        assertThat(change(actor, "SUSPENDED", 0, UUID.randomUUID()).getResponse().getStatus()).isEqualTo(200);
        var stale = change(actor, "SUSPENDED", 0, UUID.randomUUID());
        assertThat(stale.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(stale.getResponse().getContentAsString()).get("code").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(outcome(stale, "FAILURE")).isEqualTo(1);
        state("SUSPENDED", 1);
    }
}
