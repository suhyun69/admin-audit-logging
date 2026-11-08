package com.example.demo.audit;

import com.example.demo.support.PostgresTestSupport;
import com.example.demo.usecase.AuthorizationUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityHttpAuditTest.GuardProbe.class)
class SecurityHttpAuditTest extends PostgresTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    record Login(Cookie cookie,MockHttpSession session,MvcResult result) {}
    @BeforeEach void reset() {
        dropFailureTrigger();
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");
        jdbc.update("DELETE FROM admin_sessions");
    }
    @AfterEach void cleanup() { dropFailureTrigger(); }
    private void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_reject_observations ON audit_logs");
        jdbc.execute("DROP FUNCTION IF EXISTS test_reject_observations()");
    }
    private MvcResult csrf(MockHttpSession session,Cookie auth) throws Exception {
        var builder=get("/api/v1/auth/csrf");if(session!=null)builder.session(session);if(auth!=null)builder.cookie(auth);
        return mvc.perform(builder).andExpect(status().isOk()).andReturn();
    }
    private String token(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText(); }
    private Login login(String username) throws Exception {
        var csrf=csrf(null,null);var session=(MockHttpSession)csrf.getRequest().getSession(false);
        var result=mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token(csrf))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username",username,"password","local-test-password"))))
                .andExpect(status().isOk()).andReturn();
        var header=result.getResponse().getHeaders("Set-Cookie").stream().filter(value->value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        return new Login(new Cookie("AUDIT_SESSION",header.substring(14,header.indexOf(';'))),session,result);
    }
    private UUID requestId(MvcResult result) { return UUID.fromString(result.getResponse().getHeader("X-Request-ID")); }
    private int count(MvcResult result,String source) { return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source=?",Integer.class,requestId(result),source); }
    private void exactlyOneSecurityAndHttp(MvcResult result,String type) {
        assertThat(count(result,"SECURITY")).isEqualTo(1);assertThat(count(result,"HTTP")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM audit_logs WHERE request_id=? AND source='SECURITY'",String.class,requestId(result))).isEqualTo(type);
    }
    @Test
    void loginAndLogoutRecordCommittedOutcomeOnceAndRetainActorSnapshot() throws Exception {
        var actor=login("admin");exactlyOneSecurityAndHttp(actor.result(),"AUTH_LOGIN_SUCCESS");
        assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_logs WHERE request_id=? AND source='SECURITY'",Long.class,requestId(actor.result()))).isEqualTo(1L);
        var csrf=csrf(actor.session(),actor.cookie());
        var logout=mvc.perform(post("/api/v1/auth/logout").session(actor.session()).cookie(actor.cookie()).header("X-CSRF-TOKEN",token(csrf)))
                .andExpect(status().isNoContent()).andReturn();
        exactlyOneSecurityAndHttp(logout,"AUTH_LOGOUT");
        assertThat(jdbc.queryForList("SELECT actor_id FROM audit_logs WHERE request_id=?",Long.class,requestId(logout))).containsExactlyInAnyOrder(1L,1L);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM admin_sessions",Boolean.class)).isTrue();
        assertThat(MDC.get("requestId")).isNull();
    }
    @Test
    void invalidCredentialsProduceOnlyLoginFailureWithoutInputUsername() throws Exception {
        var csrf=csrf(null,null);
        var result=mvc.perform(post("/api/v1/auth/login").session((MockHttpSession)csrf.getRequest().getSession(false)).header("X-CSRF-TOKEN",token(csrf))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"private-person@example.test\",\"password\":\"wrong-secret\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        exactlyOneSecurityAndHttp(result,"AUTH_LOGIN_FAILURE");
        var row=jdbc.queryForMap("SELECT actor_name,actor_id,metadata::text metadata FROM audit_logs WHERE request_id=? AND source='SECURITY'",requestId(result));
        assertThat(row.get("actor_name")).isNull();assertThat(row.get("actor_id")).isNull();
        assertThat(row.get("metadata").toString()).doesNotContain("private-person","wrong-secret");
    }
    @Test
    void unauthenticatedRoleDenialAndCsrfEachProduceOneSecurityEvent() throws Exception {
        var unauth=mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized()).andReturn();
        exactlyOneSecurityAndHttp(unauth,"AUTH_UNAUTHENTICATED");
        var viewer=login("viewer");
        var denied=mvc.perform(get("/api/v1/audit-logs").cookie(viewer.cookie())).andExpect(status().isForbidden()).andReturn();
        exactlyOneSecurityAndHttp(denied,"AUTH_ACCESS_DENIED");
        var csrf=mvc.perform(patch("/api/v1/users/123/status").cookie(viewer.cookie())).andExpect(status().isForbidden()).andReturn();
        exactlyOneSecurityAndHttp(csrf,"AUTH_ACCESS_DENIED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM audit_logs WHERE request_id=? AND source='SECURITY'",String.class,requestId(csrf))).isEqualTo("CSRF_INVALID");
        assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_logs WHERE request_id=? AND source='SECURITY'",Long.class,requestId(csrf))).isEqualTo(3L);
    }
    @Test
    void serviceGuardDenialHandledByMvcAdviceIsAuditedOnce() throws Exception {
        var viewer=login("viewer");
        var result=mvc.perform(get("/api/v1/service-guard-probe").cookie(viewer.cookie())).andExpect(status().isForbidden()).andReturn();
        exactlyOneSecurityAndHttp(result,"AUTH_ACCESS_DENIED");
    }
    @Test
    void malformedCorrelationStillGetsHttp400AndHealthIsExcluded() throws Exception {
        var invalid=mvc.perform(get("/api/v1/auth/me").header("X-Interaction-ID","invalid-private-value"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(count(invalid,"HTTP")).isEqualTo(1);assertThat(count(invalid,"SECURITY")).isZero();
        var row=jdbc.queryForMap("SELECT interaction_id,status_code,result FROM audit_logs WHERE request_id=?",requestId(invalid));
        assertThat(row.get("interaction_id")).isNull();assertThat(row.get("status_code")).isEqualTo(400);assertThat(row.get("result")).isEqualTo("FAILURE");
        var health=mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();
        assertThat(count(health,"HTTP")).isZero();
    }
    @Test
    void remoteAddressAndBoundedUserAgentIgnoreForgedForwardedHeader() throws Exception {
        var actor=login("admin");
        var result=mvc.perform(get("/api/v1/auth/me").cookie(actor.cookie()).with(request->{request.setRemoteAddr("2001:db8::2");return request;})
                .header("X-Forwarded-For","203.0.113.99").header("User-Agent","a".repeat(600)))
                .andExpect(status().isOk()).andReturn();
        var row=jdbc.queryForMap("SELECT host(ip_address) ip,length(user_agent) length,duration_ms,actor_id FROM audit_logs WHERE request_id=? AND source='HTTP'",requestId(result));
        assertThat(row.get("ip")).isEqualTo("2001:db8::2");assertThat(row.get("length")).isEqualTo(512);
        assertThat((Long)row.get("duration_ms")).isGreaterThanOrEqualTo(0);assertThat(row.get("actor_id")).isEqualTo(1L);
    }
    @Test
    void persistenceFailureCannotReplaceSecurityOrBusinessResponse() throws Exception {
        jdbc.execute("CREATE FUNCTION test_reject_observations() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.source IN ('HTTP','SECURITY') THEN RAISE EXCEPTION 'test rejection'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_reject_observations BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION test_reject_observations()");
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        var actor=login("admin");
        mvc.perform(get("/api/v1/auth/me").cookie(actor.cookie())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)).isZero();
    }

    @TestConfiguration(proxyBeanMethods=false)
    @RestController
    static class GuardProbe {
        private final AuthorizationUseCase authorization;
        GuardProbe(AuthorizationUseCase authorization) { this.authorization=authorization; }
        @GetMapping("/api/v1/service-guard-probe")
        String probe() { authorization.requireAuditAdmin();return "ok"; }
    }
}
