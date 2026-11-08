package com.example.demo.qa;

import com.example.demo.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QA-owned fixture. All state belongs to the inherited disposable PostgreSQL container. */
abstract class AuditContractFixture extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    record Actor(Cookie cookie, MockHttpSession session, String csrf) {}
    @BeforeEach
    void ownFixture() {
        assertThat(POSTGRES.getDatabaseName()).isEqualTo("audit_test");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(POSTGRES.getDatabaseName());
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs,admin_sessions,admin_users,users RESTART IDENTITY CASCADE");
        String hash = new BCryptPasswordEncoder().encode("audit-contract-password");
        for (String role : new String[]{"ADMIN", "OPERATOR", "VIEWER"})
            jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                    role.toLowerCase(java.util.Locale.ROOT), hash, "QA " + role, role);
        jdbc.update("INSERT INTO users(id,email,name) OVERRIDING SYSTEM VALUE VALUES (123,'member123@example.test','Contract member')");
    }
    Actor login(String name) throws Exception {
        var first = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) first.getRequest().getSession(false);
        String token = json.readTree(first.getResponse().getContentAsString()).get("token").asText();
        var result = mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", name, "password", "audit-contract-password"))))
                .andExpect(status().isOk()).andReturn();
        String header = result.getResponse().getHeaders("Set-Cookie").stream().filter(value -> value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        Cookie auth = new Cookie("AUDIT_SESSION", header.substring("AUDIT_SESSION=".length(), header.indexOf(';')));
        var fresh = mvc.perform(get("/api/v1/auth/csrf").session(session).cookie(auth)).andExpect(status().isOk()).andReturn();
        return new Actor(auth, session, json.readTree(fresh.getResponse().getContentAsString()).get("token").asText());
    }
    UUID requestId(MvcResult result) { return UUID.fromString(result.getResponse().getHeader("X-Request-ID")); }
    int rows(UUID eventId) { return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE id=?", Integer.class, eventId); }
}
