package com.example.demo.auth;

import com.example.demo.support.HttpApiTestSupport;
import com.example.demo.usecase.LocalAuthUseCase;
import com.example.demo.usecase.support.AuditStreamHub;
import com.example.demo.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NonLocalDevelopmentIsolationTest extends HttpApiTestSupport {
    @Autowired ApplicationContext context;
    @Autowired AuditStreamHub hub;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Test void authenticatedNonLocalUsersStillCannotUseLocalLoginOrPublicStream() throws Exception {
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('admin',?,'Admin','ADMIN')",passwords.encode("local-test-password"));
        try {
            var admin=login("admin");
            long before=jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Long.class);
            mvc.perform(post("/api/v1/local/login").cookie(admin.auth()).session(admin.csrfSession()).header("X-CSRF-TOKEN",admin.csrfToken())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/local/audit-logs/stream").cookie(admin.auth()).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/local/accounts").cookie(admin.auth())).andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Long.class)).isEqualTo(before);
        } finally {
            jdbc.update("DELETE FROM audit_log_changes");jdbc.update("DELETE FROM audit_logs");
            jdbc.update("DELETE FROM admin_sessions");jdbc.update("DELETE FROM admin_users");
        }
    }
    @Test void nonLocalContextHasNoPasswordlessBeansOrPublicStreamAndAccountsDetects404() throws Exception {
        assertThat(context.getBeansOfType(LocalAuthUseCase.class)).isEmpty();
        assertThat(context.getBeansOfType(com.example.demo.auth.application.LocalAuthService.class)).isEmpty();
        assertThat(context.getBeansOfType(com.example.demo.controller.LocalAuthController.class)).isEmpty();
        mvc.perform(get("/api/v1/local/accounts")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)csrf.getRequest().getSession(false);
        String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/v1/local/login").session(session).header("X-CSRF-TOKEN",token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/local/audit-logs/stream").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit-logs/stream").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->hub.subscribeLocalClient(null)).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_users",Long.class)).isZero();
    }
}
