package com.example.demo.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public abstract class HttpApiTestSupport extends PostgresTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    protected record Session(Cookie auth,MockHttpSession csrfSession,String csrfToken) {}
    protected Session login(String username) throws Exception {
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)csrf.getRequest().getSession(false);
        String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        var login=mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username",username,"password","local-test-password"))))
                .andExpect(status().isOk()).andReturn();
        var header=login.getResponse().getHeaders("Set-Cookie").stream().filter(value->value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        var auth=new Cookie("AUDIT_SESSION",header.substring(14,header.indexOf(';')));
        var refreshed=mvc.perform(get("/api/v1/auth/csrf").session(session).cookie(auth)).andExpect(status().isOk()).andReturn();
        return new Session(auth,session,mapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asText());
    }
    protected UUID requestId(MvcResult result) { return UUID.fromString(result.getResponse().getHeader("X-Request-ID")); }
}
