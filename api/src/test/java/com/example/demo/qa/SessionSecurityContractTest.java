package com.example.demo.qa;

import com.example.demo.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Independent QA contracts: real application filters/controllers and a disposable PostgreSQL database. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class SessionSecurityContractTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    record Browser(Cookie auth, MockHttpSession csrfSession, MvcResult login) {}
    record Bootstrap(MockHttpSession session, String token) {}

    @BeforeEach
    void isolateFixture() {
        // Never reset a database unless the inherited Testcontainers URL and database agree.
        assertThat(POSTGRES.getDatabaseName()).isEqualTo("audit_test");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(POSTGRES.getDatabaseName());
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs,admin_sessions,admin_users,users RESTART IDENTITY CASCADE");
        String hash = new BCryptPasswordEncoder().encode("contract-password");
        for (String role : new String[]{"ADMIN", "OPERATOR", "VIEWER"}) {
            jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                    role.toLowerCase(java.util.Locale.ROOT), hash, "Contract " + role, role);
        }
        jdbc.update("INSERT INTO users(id,email,name) OVERRIDING SYSTEM VALUE VALUES (123,'security123@example.test','Security contract member')");
    }

    private Bootstrap csrf(Browser browser) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (browser != null) request.cookie(browser.auth()).session(browser.csrfSession());
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        return new Bootstrap((MockHttpSession) result.getRequest().getSession(false), body.get("token").asText());
    }

    private Browser login(String username) throws Exception {
        Bootstrap bootstrap = csrf(null);
        String oldSessionId = bootstrap.session().getId();
        var result = mvc.perform(post("/api/v1/auth/login").session(bootstrap.session())
                        .header("X-CSRF-TOKEN", bootstrap.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "contract-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username)).andReturn();
        assertThat(bootstrap.session().getId().equals(oldSessionId)).isFalse();
        String cookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        String raw = cookie.substring("AUDIT_SESSION=".length(), cookie.indexOf(';'));
        return new Browser(new Cookie("AUDIT_SESSION", raw), bootstrap.session(), result);
    }

    private UUID requestId(MvcResult result) {
        return UUID.fromString(result.getResponse().getHeader("X-Request-ID"));
    }

    private void observations(MvcResult response, String securityType) {
        UUID id = requestId(response);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='HTTP'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='SECURITY'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM audit_logs WHERE request_id=? AND source='SECURITY'", String.class, id)).isEqualTo(securityType);
    }

    @Test
    void successfulLoginOwnsIdentityAndPersistsOnlyHash() throws Exception {
        Browser admin = login("admin");
        observations(admin.login(), "AUTH_LOGIN_SUCCESS");
        String cookie = admin.login().getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        // Assert booleans to avoid printing cookie credentials even on assertion failure.
        assertThat(cookie.contains("HttpOnly") && cookie.contains("Secure") && cookie.contains("SameSite=Lax")
                && cookie.contains("Path=/") && cookie.contains("Max-Age=28800")).isTrue();
        assertThat(admin.auth().getValue().length()).isEqualTo(43);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(admin.auth().getValue().getBytes(StandardCharsets.UTF_8)));
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM admin_sessions", String.class);
        assertThat(expectedHash.equals(storedHash)).isTrue();
        assertThat(admin.auth().getValue().equals(storedHash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM expires_at-created_at)::int FROM admin_sessions", Integer.class)).isEqualTo(28800);
        var me = mvc.perform(get("/api/v1/auth/me").cookie(admin.auth()).header("X-Request-ID", "untrusted-id"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1)).andReturn();
        assertThat(requestId(me)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_logs WHERE request_id=? AND source='HTTP'", Long.class, requestId(me))).isEqualTo(1L);
    }

    @Test
    void failedLoginDoesNotPersistInputIdentityOrDuplicateSecurity() throws Exception {
        Bootstrap bootstrap = csrf(null);
        var result = mvc.perform(post("/api/v1/auth/login").session(bootstrap.session())
                        .header("X-CSRF-TOKEN", bootstrap.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"private-person@example.test\",\"password\":\"private-secret\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn();
        observations(result, "AUTH_LOGIN_FAILURE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='SECURITY' AND actor_id IS NULL AND actor_name IS NULL", Integer.class, requestId(result))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE row_to_json(audit_logs)::text LIKE '%private-person%' OR row_to_json(audit_logs)::text LIKE '%private-secret%'", Integer.class)).isZero();
    }

    @Test
    void missingCsrfAndMixedAuthenticationSessionsCannotLogout() throws Exception {
        Browser admin = login("admin");
        var missing = mvc.perform(post("/api/v1/auth/logout").cookie(admin.auth()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID")).andReturn();
        observations(missing, "AUTH_ACCESS_DENIED");
        for (String otherActor : new String[]{"admin", "viewer"}) {
            Browser other = login(otherActor);
            Bootstrap mixed = csrf(other);
            var result = mvc.perform(post("/api/v1/auth/logout").cookie(admin.auth()).session(mixed.session())
                            .header("X-CSRF-TOKEN", mixed.token()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID")).andReturn();
            observations(result, "AUTH_ACCESS_DENIED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions WHERE revoked_at IS NOT NULL", Integer.class)).isZero();
    }

    @Test
    void logoutRevokesDatabaseTokenAndExpiresBothCookies() throws Exception {
        Browser admin = login("admin");
        Bootstrap fresh = csrf(admin);
        var logout = mvc.perform(post("/api/v1/auth/logout").cookie(admin.auth()).session(fresh.session())
                        .header("X-CSRF-TOKEN", fresh.token()))
                .andExpect(status().isNoContent()).andReturn();
        observations(logout, "AUTH_LOGOUT");
        assertThat(logout.getResponse().getHeaders("Set-Cookie").size()).isEqualTo(2);
        assertThat(logout.getResponse().getHeaders("Set-Cookie").stream().allMatch(value -> value.contains("Max-Age=0"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
        var replay = mvc.perform(get("/api/v1/auth/me").cookie(admin.auth()))
                .andExpect(status().isUnauthorized()).andReturn();
        observations(replay, "AUTH_UNAUTHENTICATED");
    }

    @Test
    void expiredDisabledAndMalformedSessionsCannotAuthenticate() throws Exception {
        Browser expired = login("operator");
        jdbc.update("UPDATE admin_sessions SET expires_at=now()-interval '1 second'");
        mvc.perform(get("/api/v1/auth/me").cookie(expired.auth())).andExpect(status().isUnauthorized());
        Browser disabled = login("viewer");
        jdbc.update("UPDATE admin_users SET enabled=FALSE WHERE username='viewer'");
        mvc.perform(get("/api/v1/auth/me").cookie(disabled.auth())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("AUDIT_SESSION", "malformed"))).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfCookieAloneNeverAuthenticatesAndPublicLoginRequiresCsrf() throws Exception {
        Bootstrap anonymous = csrf(null);
        var me = mvc.perform(get("/api/v1/auth/me").session(anonymous.session()))
                .andExpect(status().isUnauthorized()).andReturn();
        observations(me, "AUTH_UNAUTHENTICATED");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"contract-password\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions", Integer.class)).isZero();
    }

    @Test
    void httpCorrelationAndPrivacySurviveFilterCleanup() throws Exception {
        Browser admin = login("admin");
        UUID interaction = UUID.randomUUID();
        var response = mvc.perform(get("/api/v1/auth/me").cookie(admin.auth()).queryParam("q", "private-query@example.test")
                        .header("X-Interaction-ID", interaction.toString()).header("X-Forwarded-For", "203.0.113.22")
                        .header("User-Agent", "x".repeat(700)).with(request -> { request.setRemoteAddr("2001:db8::8"); return request; }))
                .andExpect(status().isOk()).andReturn();
        var row = jdbc.queryForMap("SELECT actor_id,interaction_id,request_path,host(ip_address) ip,length(user_agent) agent_length,duration_ms FROM audit_logs WHERE request_id=? AND source='HTTP'", requestId(response));
        assertThat(row.get("actor_id")).isEqualTo(1L);
        assertThat(row.get("interaction_id")).isEqualTo(interaction);
        assertThat(row.get("request_path")).isEqualTo("/api/v1/auth/me");
        assertThat(row.get("ip")).isEqualTo("2001:db8::8");
        assertThat(row.get("agent_length")).isEqualTo(512);
        assertThat((Long) row.get("duration_ms")).isGreaterThanOrEqualTo(0);
        assertThat(com.example.demo.common.request.RequestContext.current()).isEmpty();
        assertThat(org.slf4j.MDC.get("requestId")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE row_to_json(audit_logs)::text LIKE '%private-query%'", Integer.class)).isZero();
    }

    private MvcResult change(Browser actor, Bootstrap token, String target, long version) throws Exception {
        return mvc.perform(patch("/api/v1/users/123/status").cookie(actor.auth()).session(token.session())
                        .header("X-CSRF-TOKEN", token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", target, "version", version))))
                .andReturn();
    }

    @Test
    void actualWriteEndpointHasAdminOperatorPositiveControlsAndViewerCannotChangeDatabase() throws Exception {
        Browser admin = login("admin");
        assertThat(change(admin, csrf(admin), "SUSPENDED", 0).getResponse().getStatus()).isEqualTo(200);
        Browser operator = login("operator");
        assertThat(change(operator, csrf(operator), "ACTIVE", 1).getResponse().getStatus()).isEqualTo(200);
        Browser viewer = login("viewer");
        var denied = change(viewer, csrf(viewer), "SUSPENDED", 2);
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(json.readTree(denied.getResponse().getContentAsString()).get("code").asText()).isEqualTo("ACCESS_DENIED");
        observations(denied, "AUTH_ACCESS_DENIED");
        var member = jdbc.queryForMap("SELECT status,version FROM users WHERE id=123");
        assertThat(member.get("status")).isEqualTo("ACTIVE");
        assertThat(member.get("version")).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='BUSINESS'", Integer.class, requestId(denied))).isZero();
    }

    @Test
    void oneCsrfTokenSurvivesThreeSequentialAndTwoConcurrentRealMutations() throws Exception {
        Browser admin = login("admin");
        Bootstrap token = csrf(admin);
        String sessionId = token.session().getId();
        var updatedAt = jdbc.queryForObject("SELECT updated_at FROM users WHERE id=123", java.sql.Timestamp.class);
        for (int n = 0; n < 3; n++)
            assertThat(change(admin, token, "ACTIVE", 0).getResponse().getStatus()).isEqualTo(200);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int n = 0; n < 2; n++) tasks.add(workers.submit(() -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Mutation start timeout");
                return change(admin, token, "ACTIVE", 0).getResponse().getStatus();
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var result : tasks) assertThat(result.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(token.session().getId().equals(sessionId)).isTrue();
        assertThat(csrf(admin).token().equals(token.token())).isTrue();
        assertThat(token.session().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        var row = jdbc.queryForMap("SELECT status,version,updated_at FROM users WHERE id=123");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("version")).isEqualTo(0L);
        assertThat(row.get("updated_at")).isEqualTo(updatedAt);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE event_type='DATA_UPDATE'", Integer.class)).isZero();
    }

    @Test
    void actualAuditEndpointHasAdminPositiveControlAndOwnScopeCannotBeExpanded() throws Exception {
        Browser admin = login("admin");
        Browser operator = login("operator");
        Browser viewer = login("viewer");
        UUID interaction = UUID.randomUUID();
        for (long actorId = 1; actorId <= 3; actorId++)
            jdbc.update("INSERT INTO audit_logs(id,event_type,action,actor_id,source,result,resource_type,resource_id,interaction_id) VALUES (?,'DATA_READ','USER_DETAIL',?,'BUSINESS','SUCCESS','USER','123',?)",
                    UUID.randomUUID(), actorId, interaction);
        var global = mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("interactionId", interaction.toString()).param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(global.getResponse().getContentAsString()).get("items").size()).isEqualTo(3);
        var filtered = mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("interactionId", interaction.toString()).param("actorId", "2"))
                .andExpect(status().isOk()).andReturn();
        var filteredItems = json.readTree(filtered.getResponse().getContentAsString()).get("items");
        assertThat(filteredItems.size()).isEqualTo(1);
        assertThat(filteredItems.get(0).get("actorId").asLong()).isEqualTo(2L);
        for (var subject : Map.of(2L, operator, 3L, viewer).entrySet()) {
            var denied = mvc.perform(get("/api/v1/audit-logs").cookie(subject.getValue().auth()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED")).andReturn();
            observations(denied, "AUTH_ACCESS_DENIED");
            var mine = mvc.perform(get("/api/v1/audit-logs/me").cookie(subject.getValue().auth()).param("interactionId", interaction.toString()))
                    .andExpect(status().isOk()).andReturn();
            var items = json.readTree(mine.getResponse().getContentAsString()).get("items");
            assertThat(items.size()).isEqualTo(1); // Positive control: scope assertions are never vacuously true.
            assertThat(items.get(0).get("actorId").asLong()).isEqualTo(subject.getKey());
            for (String suppliedActor : new String[]{"", "1", subject.getKey().toString()})
                mvc.perform(get("/api/v1/audit-logs/me").cookie(subject.getValue().auth()).param("actorId", suppliedActor))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }
}
