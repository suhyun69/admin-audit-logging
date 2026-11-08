package com.example.demo.auth;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.servlet.session.cookie.name=AUDIT_CSRF_SESSION")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(CsrfSessionReuseTest.MutationProbe.class)
class CsrfSessionReuseTest extends HttpApiTestSupport {
    private static final String PROBE="/api/v1/csrf-session-reuse-probe";
    @LocalServerPort private int port;

    @Test
    void sameCsrfTokenAndSessionIdSurviveRepeatedMutationsWithoutStoringAuthentication() throws Exception {
        var actor=login("admin");String sessionId=actor.csrfSession().getId();
        for(int n=0;n<3;n++) {
            mvc.perform(post(PROBE).cookie(actor.auth()).session(actor.csrfSession()).header("X-CSRF-TOKEN",actor.csrfToken()))
                    .andExpect(status().isNoContent());
            assertThat(actor.csrfSession().getId()).isEqualTo(sessionId);
            var stored=(CsrfToken)actor.csrfSession().getAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
            assertThat(stored).isNotNull();assertThat(stored.getToken()).isEqualTo(actor.csrfToken());
            assertThat(actor.csrfSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        }
    }
    @Test
    void concurrentMutationsUsingSameCsrfSessionAreBothAccepted() throws Exception {
        var actor=login("admin");var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var tasks=new java.util.ArrayList<Future<Integer>>();
            for(int n=0;n<2;n++)tasks.add(executor.submit(()->{
                ready.countDown();start.await();
                return mvc.perform(post(PROBE).cookie(actor.auth()).session(actor.csrfSession()).header("X-CSRF-TOKEN",actor.csrfToken()))
                        .andReturn().getResponse().getStatus();
            }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(java.util.List.of(tasks.get(0).get(10,TimeUnit.SECONDS),tasks.get(1).get(10,TimeUnit.SECONDS)))
                    .containsExactly(204,204);
        } finally { start.countDown();executor.shutdownNow(); }
    }
    @Test
    void realServletHttpClientCanReuseFreshTokenForMultiplePosts() throws Exception {
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try(var client=HttpClient.newBuilder().cookieHandler(cookies).build()) {
            var before=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(before.statusCode()).isEqualTo(200);
            String initialToken=mapper.readTree(before.body()).get("token").asText();
            String initialSessionId=cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("AUDIT_CSRF_SESSION")).findFirst().orElseThrow().getValue();
            var login=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",initialToken)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"local-test-password\"}")).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            String authenticatedSessionId=cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("AUDIT_CSRF_SESSION")).findFirst().orElseThrow().getValue();
            assertThat(authenticatedSessionId).isNotEqualTo(initialSessionId);
            var fresh=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            String token=mapper.readTree(fresh.body()).get("token").asText();assertThat(token).isNotEqualTo(initialToken);
            for(int n=0;n<3;n++) {
                var response=client.send(HttpRequest.newBuilder(uri(PROBE)).header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(204);
                assertThat(cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("AUDIT_CSRF_SESSION")).findFirst().orElseThrow().getValue()).isEqualTo(authenticatedSessionId);
            }
            var logout=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/logout")).header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(logout.statusCode()).isEqualTo(204);
            assertThat(cookies.getCookieStore().getCookies()).noneMatch(cookie->cookie.getName().equals("AUDIT_SESSION")||cookie.getName().equals("AUDIT_CSRF_SESSION"));
            assertThat(client.send(HttpRequest.newBuilder(uri("/api/v1/auth/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            var anonymous=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(anonymous.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(anonymous.body()).get("token").asText()).isNotEqualTo(token);
            assertThat(cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("AUDIT_CSRF_SESSION")).findFirst().orElseThrow().getValue()).isNotEqualTo(authenticatedSessionId);
        }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:"+port+path); }

    @TestConfiguration(proxyBeanMethods=false)
    @RestController
    static class MutationProbe {
        @PostMapping(PROBE)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void accept() {}
    }
}
