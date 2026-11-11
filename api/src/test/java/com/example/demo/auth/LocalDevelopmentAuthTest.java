package com.example.demo.auth;

import com.example.demo.support.HttpApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalDevelopmentAuthTest extends HttpApiTestSupport {
    @Test void enabledAccountListComesFromDatabaseWithoutCredentialFields() throws Exception {
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) SELECT 'local-dynamic',password_hash,'Dynamic account','VIEWER' FROM admin_users WHERE username='admin'");
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role,enabled) SELECT 'local-disabled',password_hash,'Disabled account','ADMIN',false FROM admin_users WHERE username='admin'");
        var response=mvc.perform(get("/api/v1/local/accounts")).andExpect(status().isOk()).andReturn().getResponse();
        var items=mapper.readTree(response.getContentAsString()).get("items");
        assertThat(items.toString()).contains("local-dynamic").doesNotContain("local-disabled","password","hash","token","local-test-password","$2");
        for(var item:items) assertThat(item.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("id","username","displayName","role");
        jdbc.update("DELETE FROM admin_users WHERE username IN ('local-dynamic','local-disabled')");
    }
    @Test void passwordlessLoginUsesNormalSessionAndCsrfRotationForEveryRole() throws Exception {
        for(String username:new String[]{"admin","operator","viewer"}) {
            var csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn();
            var session=(MockHttpSession)csrf.getRequest().getSession(false);String oldId=session.getId();
            String oldToken=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
            var result=mvc.perform(post("/api/v1/local/login").session(session).header("X-CSRF-TOKEN",oldToken)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("username",username))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username)).andReturn();
            assertThat(session.getId()).isNotEqualTo(oldId);
            String header=result.getResponse().getHeaders("Set-Cookie").stream().filter(value->value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
            assertThat(header).contains("HttpOnly","SameSite=Lax","Max-Age=28800");
            String token=header.substring(14,header.indexOf(';'));var auth=new Cookie("AUDIT_SESSION",token);
            mvc.perform(get("/api/v1/auth/me").cookie(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));
            var refreshed=mvc.perform(get("/api/v1/auth/csrf").cookie(auth).session(session)).andExpect(status().isOk()).andReturn();
            assertThat(mapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asText()).isNotEqualTo(oldToken);
            var row=jdbc.queryForMap("SELECT token_hash,extract(epoch from expires_at-created_at)::bigint lifetime FROM admin_sessions WHERE admin_user_id=(SELECT id FROM admin_users WHERE username=?) ORDER BY created_at DESC LIMIT 1",username);
            assertThat(row.get("token_hash")).isNotEqualTo(token);assertThat(row.get("token_hash").toString()).matches("[0-9a-f]{64}");
            assertThat(((Number)row.get("lifetime")).longValue()).isEqualTo(Duration.ofHours(8).toSeconds());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE event_type='AUTH_LOGIN_SUCCESS' AND source='SECURITY' AND request_id=?",Integer.class,requestId(result))).isEqualTo(1);
        }
    }
    @Test void csrfMissingUnknownAndDisabledAccountsCannotIssueSessionsAndNormalLoginStillNeedsPassword() throws Exception {
        long before=jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Long.class);
        mvc.perform(post("/api/v1/local/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE admin_users SET enabled=false WHERE username='viewer'");
        try {
            for(String username:new String[]{"not-a-local-account","viewer"}) {
                var csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn();var session=(MockHttpSession)csrf.getRequest().getSession(false);
                String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
                mvc.perform(post("/api/v1/local/login").session(session).header("X-CSRF-TOKEN",token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("username",username))))
                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
                mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\"}"))
                        .andExpect(status().isBadRequest());
            }
        } finally {jdbc.update("UPDATE admin_users SET enabled=true WHERE username='viewer'");}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Long.class)).isEqualTo(before);
    }
}
