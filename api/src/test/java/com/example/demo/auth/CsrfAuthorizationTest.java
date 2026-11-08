package com.example.demo.auth;

import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.support.PostgresTestSupport;
import com.example.demo.usecase.AuthorizationUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.example.demo.controller.AuthController;
import com.example.demo.common.web.GlobalExceptionHandler;
import com.example.demo.common.request.RequestContextFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CsrfAuthorizationTest extends PostgresTestSupport {
    private MockMvc mvc;
    @Autowired private AuthController authController;
    @Autowired private RequestContextFilter requestFilter;
    @Autowired private FilterChainProxy securityFilter;
    @BeforeEach void mvcWithOnlyProbeAndRealAuthenticationController() {
        mvc=MockMvcBuilders.standaloneSetup(new ProtectedProbe(),authController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(requestFilter,securityFilter).build();
    }
    @Autowired private ObjectMapper mapper;
    @Autowired private AuthorizationUseCase authorization;
    record Login(Cookie cookie,MockHttpSession session,String previousToken) {}
    private MvcResult csrf(MockHttpSession session,Cookie auth) throws Exception {
        var builder=get("/api/v1/auth/csrf");
        if(session!=null)builder.session(session);
        if(auth!=null)builder.cookie(auth);
        return mvc.perform(builder).andExpect(status().isOk()).andReturn();
    }
    private String token(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText(); }
    private Login login(String username) throws Exception {
        var csrf=csrf(null,null);var session=(MockHttpSession)csrf.getRequest().getSession(false);
        String before=token(csrf);
        var result=mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",before)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username",username,"password","local-test-password"))))
                .andExpect(status().isOk()).andReturn();
        var header=result.getResponse().getHeaders("Set-Cookie").stream().filter(value->value.startsWith("AUDIT_SESSION=")).findFirst().orElseThrow();
        return new Login(new Cookie("AUDIT_SESSION",header.substring(14,header.indexOf(';'))),session,before);
    }
    @AfterEach void cleanup() { RequestContext.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void loginInvalidatesOldCsrfAndFreshTokenAllowsSameSessionMutation() throws Exception {
        var actor=login("admin");
        mvc.perform(patch("/api/v1/users/123/status").cookie(actor.cookie()).session(actor.session()).header("X-CSRF-TOKEN",actor.previousToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        String fresh=token(csrf(actor.session(),actor.cookie()));
        assertThat(fresh).isNotEqualTo(actor.previousToken());
        mvc.perform(patch("/api/v1/users/123/status").cookie(actor.cookie()).session(actor.session()).header("X-CSRF-TOKEN",fresh))
                .andExpect(status().isNoContent());
    }
    @Test
    void csrfCannotBeMixedWithDifferentOpaqueSessionEvenForSameActor() throws Exception {
        for(String other:new String[]{"viewer","admin"}) {
            var first=login("admin");var second=login(other);
            String secondToken=token(csrf(second.session(),second.cookie()));
            mvc.perform(patch("/api/v1/users/123/status").cookie(first.cookie()).session(second.session()).header("X-CSRF-TOKEN",secondToken))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
    }
    @Test
    void roleMatchersReserveGlobalAuditAndWritesButAllowOwnReadEndpoints() throws Exception {
        for(String username:new String[]{"admin","operator","viewer"}) {
            var actor=login(username);String csrf=token(csrf(actor.session(),actor.cookie()));
            mvc.perform(patch("/api/v1/users/123/status").cookie(actor.cookie()).session(actor.session()).header("X-CSRF-TOKEN",csrf))
                    .andExpect(status().is(username.equals("viewer")?403:204));
            for(String path:new String[]{"/api/v1/audit-logs","/api/v1/audit-logs/"+UUID.randomUUID(),"/api/v1/audit-logs/interactions/"+UUID.randomUUID(),"/api/v1/settings/admins"})
                mvc.perform(get(path).cookie(actor.cookie())).andExpect(status().is(username.equals("admin")?200:403));
            mvc.perform(get("/api/v1/audit-logs/summary").cookie(actor.cookie())).andExpect(status().isOk());
            mvc.perform(get("/api/v1/audit-logs/me").cookie(actor.cookie())).andExpect(status().isOk());
            mvc.perform(get("/api/v1/users").cookie(actor.cookie())).andExpect(status().isOk());
        }
    }
    @Test
    void serviceGuardRejectsViewerEvenWhenCalledWithoutController() {
        for(String role:new String[]{"ADMIN","OPERATOR","VIEWER"}) {
            var context=new RequestContext(UUID.randomUUID(),null,Instant.now());
            context.setActor(new ActorSnapshot(1,"Server actor",role,UUID.randomUUID()));RequestContext.install(context);
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("Server actor",null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
            assertThat(authorization.requireAuthenticated().id()).isEqualTo(1);
            if(role.equals("VIEWER"))assertThatThrownBy(authorization::requireUserWrite).isInstanceOf(AccessDeniedException.class);
            else assertThat(authorization.requireUserWrite().role()).isEqualTo(role);
            if(!role.equals("ADMIN"))assertThatThrownBy(authorization::requireAuditAdmin).isInstanceOf(AccessDeniedException.class);
            else assertThat(authorization.requireAuditAdmin().role()).isEqualTo("ADMIN");
            if(!role.equals("ADMIN"))assertThatThrownBy(authorization::requireAdminSettings).isInstanceOf(AccessDeniedException.class);
            else assertThat(authorization.requireAdminSettings().role()).isEqualTo("ADMIN");
        }
        SecurityContextHolder.clearContext();RequestContext.clear();
        assertThatThrownBy(authorization::requireAuthenticated).isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    /** Standalone-only probe; TestComponent excludes it from component scanning in any application context. */
    @TestComponent
    @RestController
    static class ProtectedProbe {
        @PatchMapping("/api/v1/users/{id}/status")
        @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
        void patch(@PathVariable long id) {}
        @GetMapping({"/api/v1/users","/api/v1/audit-logs","/api/v1/audit-logs/me","/api/v1/audit-logs/summary","/api/v1/audit-logs/{id}","/api/v1/audit-logs/interactions/{id}","/api/v1/settings/admins"})
        String read() { return "ok"; }
    }
}
