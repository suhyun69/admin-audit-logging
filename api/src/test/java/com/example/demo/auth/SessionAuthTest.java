package com.example.demo.auth;

import com.example.demo.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SessionAuthTest extends PostgresTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM admin_sessions");
        jdbc.update("UPDATE admin_users SET enabled=TRUE");
    }
    private MvcResult csrf(MockHttpSession session,Cookie auth) throws Exception {
        var request=get("/api/v1/auth/csrf");
        if(session!=null)request.session(session);
        if(auth!=null)request.cookie(auth);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }
    private String token(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText(); }
    private Cookie cookie(MvcResult result) {
        var header=result.getResponse().getHeaders("Set-Cookie").stream().filter(value->value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        return new Cookie("AUDIT_SESSION",header.substring("AUDIT_SESSION=".length(),header.indexOf(';')));
    }
    private MvcResult login(String name) throws Exception {
        var csrf=csrf(null,null);
        return mvc.perform(post("/api/v1/auth/login").session((MockHttpSession)csrf.getRequest().getSession(false))
                .header("X-CSRF-TOKEN",token(csrf)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username",name,"password","local-test-password"))))
                .andExpect(status().isOk()).andReturn();
    }
    @Test
    void loginStoresOnlyHashAndIssuesBoundedHttpOnlyCookieWithRotatedCsrfSession() throws Exception {
        var csrf=csrf(null,null);var session=(MockHttpSession)csrf.getRequest().getSession(false);var oldId=session.getId();
        var login=mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token(csrf))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"local-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.sessionId").doesNotExist()).andReturn();
        assertThat(session.getId()).isNotEqualTo(oldId);
        var cookie=cookie(login);assertThat(cookie.getValue()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains("HttpOnly","SameSite=Lax","Path=/","Max-Age=28800").doesNotContain("Secure");
        var row=jdbc.queryForMap("SELECT token_hash,extract(epoch FROM(expires_at-created_at)) seconds FROM admin_sessions");
        var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cookie.getValue().getBytes(StandardCharsets.UTF_8)));
        assertThat(row.get("token_hash")).isEqualTo(hash).isNotEqualTo(cookie.getValue());
        assertThat(((Number)row.get("seconds")).longValue()).isEqualTo(28800);
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
    }
    @Test
    void badCredentialsDisabledAndOversizedUtf8PasswordsDoNotIssueSessions() throws Exception {
        for(String username:new String[]{"admin","unknown"}) {
            var csrf=csrf(null,null);
            mvc.perform(post("/api/v1/auth/login").session((MockHttpSession)csrf.getRequest().getSession(false)).header("X-CSRF-TOKEN",token(csrf))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username",username,"password","wrong"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        jdbc.update("UPDATE admin_users SET enabled=FALSE WHERE username='admin'");
        var csrf=csrf(null,null);
        mvc.perform(post("/api/v1/auth/login").session((MockHttpSession)csrf.getRequest().getSession(false)).header("X-CSRF-TOKEN",token(csrf))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"local-test-password\"}"))
                .andExpect(status().isUnauthorized());
        csrf=csrf(null,null);
        mvc.perform(post("/api/v1/auth/login").session((MockHttpSession)csrf.getRequest().getSession(false)).header("X-CSRF-TOKEN",token(csrf))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username","admin","password","가".repeat(25)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Integer.class)).isZero();
    }
    @Test
    void logoutRevokesTokenAndOldTokenCannotBeReused() throws Exception {
        var login=login("viewer");var auth=cookie(login);var session=(MockHttpSession)login.getRequest().getSession(false);
        var csrf=csrf(session,auth);
        var logout=mvc.perform(post("/api/v1/auth/logout").cookie(auth).session(session).header("X-CSRF-TOKEN",token(csrf)))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getHeaders("Set-Cookie")).hasSize(2).allSatisfy(value->assertThat(value).contains("Max-Age=0","HttpOnly","SameSite=Lax"));
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM admin_sessions",Boolean.class)).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(auth)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
    @Test
    void expirationAndDisabledAccountInvalidateAlreadyIssuedCookie() throws Exception {
        var expired=cookie(login("admin"));
        jdbc.update("UPDATE admin_sessions SET expires_at=now()-interval '1 second'");
        mvc.perform(get("/api/v1/auth/me").cookie(expired)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/csrf").cookie(expired)).andExpect(status().isOk());
        var disabled=cookie(login("operator"));
        jdbc.update("UPDATE admin_users SET enabled=FALSE WHERE username='operator'");
        mvc.perform(get("/api/v1/auth/me").cookie(disabled)).andExpect(status().isUnauthorized());
    }
    @Test
    void csrfIsRequiredEvenForPublicLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"local-test-password\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Integer.class)).isZero();
    }
}
