package com.example.demo.audit;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.servlet.session.cookie.name=AUDIT_CSRF_SESSION")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(ClientEventsTest.TestClockConfiguration.class)
class ClientEventsTest extends HttpApiTestSupport {
    @Autowired private MutableClock clock;
    @LocalServerPort private int port;
    private Session admin;
    @BeforeEach void setup() throws Exception {
        clock.now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(30);
        admin=login("admin");
    }
    private LinkedHashMap<String,Object> payload() {
        var map=new LinkedHashMap<String,Object>();map.put("eventId",UUID.randomUUID().toString());map.put("interactionId",UUID.randomUUID().toString());
        map.put("eventType","BUTTON_CLICK");map.put("action","USER_SUSPEND_CLICK");map.put("resourceType","USER");map.put("resourceId","123");
        map.put("pagePath","/users/123?q=private-person@example.test#secret");map.put("occurredAt",clock.instant().toString());return map;
    }
    private MvcResult send(Session actor,Map<String,Object> payload) throws Exception {
        return raw(actor,mapper.writeValueAsBytes(payload),payload.get("interactionId").toString());
    }
    private MvcResult raw(Session actor,byte[] bytes,String interaction) throws Exception {
        return mvc.perform(post("/api/v1/audit-events/client").cookie(actor.auth()).session(actor.csrfSession()).header("X-CSRF-TOKEN",actor.csrfToken())
                .header("X-Interaction-ID",interaction).contentType(MediaType.APPLICATION_JSON).content(bytes)).andReturn();
    }
    private int rows(Map<String,Object> payload) { return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE id=? AND source='CLIENT'",Integer.class,UUID.fromString(payload.get("eventId").toString())); }
    @Test void recordsServerIdentityAttemptedResultAndSanitizedPageWithStableReplayDigest() throws Exception {
        var event=payload();var first=send(admin,event);assertThat(first.getResponse().getStatus()).isEqualTo(201);
        var row=jdbc.queryForMap("SELECT actor_id,actor_role,source,result,session_id,request_id,metadata::text metadata,metadata->>'pagePath' page,metadata->>'payloadDigest' digest FROM audit_logs WHERE id=?",UUID.fromString(event.get("eventId").toString()));
        assertThat(row.get("actor_id")).isEqualTo(1L);assertThat(row.get("actor_role")).isEqualTo("ADMIN");assertThat(row.get("source")).isEqualTo("CLIENT");assertThat(row.get("result")).isEqualTo("ATTEMPTED");
        assertThat(row.get("session_id")).isNotNull();assertThat(row.get("request_id")).isEqualTo(requestId(first));
        assertThat(row.get("page")).isEqualTo("/users/123");assertThat(row.get("digest").toString()).matches("[0-9a-f]{64}");
        assertThat(row.get("metadata").toString()).doesNotContain("private-person","secret","?","#");
        var reordered=new LinkedHashMap<String,Object>();var keys=new ArrayList<>(event.keySet());Collections.reverse(keys);for(String key:keys)reordered.put(key,event.get(key));
        reordered.put("pagePath","/users/123?q=another-private-value");
        assertThat(send(admin,reordered).getResponse().getStatus()).isEqualTo(200);
        clock.now=clock.instant().plus(Duration.ofHours(25));var relogin=login("admin");
        assertThat(send(relogin,event).getResponse().getStatus()).isEqualTo(200);assertThat(rows(event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT metadata->>'payloadDigest' FROM audit_logs WHERE id=?",String.class,UUID.fromString(event.get("eventId").toString()))).isEqualTo(row.get("digest"));
    }
    @Test void acceptsEveryClientCatalogTypeWithOptionalResourceOmitted() throws Exception {
        for(String[] values:new String[][]{{"PAGE_VIEW","PAGE_ENTER","/dashboard"},{"USER_SEARCH","USER_SEARCH_SUBMIT","/users"},{"FILTER_CHANGE","USER_STATUS_FILTER","/users"},{"FILTER_CHANGE","AUDIT_FILTER_CHANGE","/audit-logs/me"}}) {
            var event=payload();event.put("eventType",values[0]);event.put("action",values[1]);event.put("pagePath",values[2]);event.remove("resourceType");event.remove("resourceId");
            assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(201);
            assertThat(jdbc.queryForObject("SELECT metadata->>'pagePath' FROM audit_logs WHERE id=?",String.class,UUID.fromString(event.get("eventId").toString()))).isEqualTo(values[2]);
        }
    }
    private static final List<String> MODAL_ACTIONS=List.of("USER_SUSPEND_CONFIRM_CLICK","USER_ACTIVATE_CONFIRM_CLICK",
            "USER_STATUS_CANCEL_CLICK","USER_STATUS_CLOSE_CLICK");
    @Test void acceptsModalButtonActionsWithServerIdentityAndIdempotentRetry() throws Exception {
        for(String action:MODAL_ACTIONS) {
            var event=payload();event.put("action",action);
            assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(201);
            assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(200);
            assertThat(rows(event)).isEqualTo(1);
            var row=jdbc.queryForMap("SELECT actor_id,action,result,resource_type,resource_id,metadata->>'pagePath' page FROM audit_logs WHERE id=?",
                    UUID.fromString(event.get("eventId").toString()));
            assertThat(row.get("actor_id")).isEqualTo(1L);
            assertThat(row.get("action")).isEqualTo(action);
            assertThat(row.get("result")).isEqualTo("ATTEMPTED");
            assertThat(row.get("resource_type")).isEqualTo("USER");
            assertThat(row.get("resource_id")).isEqualTo("123");
            assertThat(row.get("page")).isEqualTo("/users/123");
        }
    }
    @Test void modalButtonActionsStillRequireMatchingUserResourceAndDetailPage() throws Exception {
        for(String action:MODAL_ACTIONS) {
            for(Map<String,Object> override:List.<Map<String,Object>>of(Map.of("pagePath","/users"),Map.of("pagePath","/users/124"),
                    Map.of("resourceId","124"),Map.of("resourceId","0"),
                    Map.of("resourceType","AUDIT_LOG","resourceId",UUID.randomUUID().toString()))) {
                var event=payload();event.put("action",action);event.putAll(override);
                assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(400);
                assertThat(rows(event)).isZero();
            }
            var noResource=payload();noResource.put("action",action);noResource.remove("resourceType");noResource.remove("resourceId");
            assertThat(send(admin,noResource).getResponse().getStatus()).isEqualTo(400);
            assertThat(rows(noResource)).isZero();
        }
    }
    @Test void forgedIdentityUnknownFieldsDuplicateKeysAndTrailingJsonAreRejected() throws Exception {
        for(String field:new String[]{"actorId","sessionId","result","receivedAt"}) {
            var event=payload();event.put(field,"forged");assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(400);assertThat(rows(event)).isZero();
        }
        var event=payload();String json=mapper.writeValueAsString(event);
        assertThat(raw(admin,(json.substring(0,json.length()-1)+",\"eventId\":\""+UUID.randomUUID()+"\"}").getBytes(StandardCharsets.UTF_8),event.get("interactionId").toString()).getResponse().getStatus()).isEqualTo(400);
        assertThat(raw(admin,(json+" {}").getBytes(StandardCharsets.UTF_8),event.get("interactionId").toString()).getResponse().getStatus()).isEqualTo(400);
        assertThat(rows(event)).isZero();
    }
    @Test void validatesTypeActionResourcePathTimeAndHeaderBodyCorrelation() throws Exception {
        for(Map<String,Object> override:List.<Map<String,Object>>of(Map.of("action","PAGE_ENTER"),Map.of("resourceId","0"),Map.of("resourceType","AUDIT_LOG","resourceId","not-uuid"),
                Map.of("pagePath","/users/124"),Map.of("pagePath","/private/private-person@example.test"),Map.of("eventType","DATA_UPDATE"),
                Map.of("occurredAt",clock.instant().minus(Duration.ofHours(25)).toString()),Map.of("occurredAt",clock.instant().plus(Duration.ofMinutes(6)).toString()))) {
            var event=payload();event.putAll(override);assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(400);assertThat(rows(event)).isZero();
        }
        var missingPair=payload();missingPair.remove("resourceId");assertThat(send(admin,missingPair).getResponse().getStatus()).isEqualTo(400);
        var mismatch=payload();assertThat(raw(admin,mapper.writeValueAsBytes(mismatch),UUID.randomUUID().toString()).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void idCollisionWithDifferentActorPayloadOrHttpSourceIs409WithoutOwnerInformation() throws Exception {
        var event=payload();assertThat(send(admin,event).getResponse().getStatus()).isEqualTo(201);
        var changed=new LinkedHashMap<>(event);changed.put("action","USER_ACTIVATE_CLICK");
        assertThat(send(admin,changed).getResponse().getStatus()).isEqualTo(409);
        var other=send(login("viewer"),event);assertThat(other.getResponse().getStatus()).isEqualTo(409);
        assertThat(mapper.readTree(other.getResponse().getContentAsString()).get("code").asText()).isEqualTo("EVENT_CONFLICT");
        var sourceCollision=payload();sourceCollision.put("eventId",jdbc.queryForObject("SELECT id FROM audit_logs WHERE source='HTTP' LIMIT 1",UUID.class).toString());
        assertThat(send(admin,sourceCollision).getResponse().getStatus()).isEqualTo(409);assertThat(rows(event)).isEqualTo(1);assertThat(rows(sourceCollision)).isZero();
        mvc.perform(get("/api/v1/users/123").cookie(admin.auth())).andReturn();
        var businessCollision=payload();businessCollision.put("eventId",jdbc.queryForObject("SELECT id FROM audit_logs WHERE source='BUSINESS' LIMIT 1",UUID.class).toString());
        assertThat(send(admin,businessCollision).getResponse().getStatus()).isEqualTo(409);assertThat(rows(businessCollision)).isZero();
    }
    @Test void concurrentSameEventCreatesExactlyOneRowWith201And200() throws Exception {
        var event=payload();byte[] bytes=mapper.writeValueAsBytes(event);String interaction=event.get("interactionId").toString();
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<Future<Integer>>();
            for(int n=0;n<2;n++)tasks.add(executor.submit(()->{ready.countDown();start.await();return raw(admin,bytes,interaction).getResponse().getStatus();}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(tasks.get(0).get(10,TimeUnit.SECONDS),tasks.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);assertThat(rows(event)).isEqualTo(1);
        } finally { start.countDown();executor.shutdownNow(); }
    }
    @Test void rawByteLimitAccepts8192AndRejects8193IncludingChunkedWithoutContentLength() throws Exception {
        var exact=payload();byte[] body=mapper.writeValueAsBytes(exact);var padded=Arrays.copyOf(body,8192);Arrays.fill(padded,body.length,padded.length,(byte)' ');
        assertThat(raw(admin,padded,exact.get("interactionId").toString()).getResponse().getStatus()).isEqualTo(201);
        var oversized=payload();body=mapper.writeValueAsBytes(oversized);var tooLarge=Arrays.copyOf(body,8193);Arrays.fill(tooLarge,body.length,tooLarge.length,(byte)' ');
        var rejected=raw(admin,tooLarge,oversized.get("interactionId").toString());assertThat(rejected.getResponse().getStatus()).isEqualTo(413);assertThat(rows(oversized)).isZero();
        assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_logs WHERE request_id=? AND source='HTTP'",Long.class,requestId(rejected))).isEqualTo(1L);
        var utf8=payload();utf8.put("pagePath","/users/123?q="+"가".repeat(3000));
        assertThat(send(admin,utf8).getResponse().getStatus()).isEqualTo(413);assertThat(rows(utf8)).isZero();
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try(var client=HttpClient.newBuilder().cookieHandler(cookies).version(HttpClient.Version.HTTP_1_1).build()) {
            var csrf=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());String token=mapper.readTree(csrf.body()).get("token").asText();
            var login=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"local-test-password\"}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(login.statusCode()).isEqualTo(200);
            csrf=client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());token=mapper.readTree(csrf.body()).get("token").asText();
            var publisher=HttpRequest.BodyPublishers.ofInputStream(()->new ByteArrayInputStream(tooLarge));assertThat(publisher.contentLength()).isEqualTo(-1);
            for(String path:new String[]{"/api/v1/audit-events/client","/api/v1/audit-events/%63lient"}) {
                var response=client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type","application/json").header("X-CSRF-TOKEN",token)
                        .POST(publisher).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(413);assertThat(rows(oversized)).isZero();
            }
        }
    }
    @Test void sixtyNewEventsPerActorAreAllowedAndRetryDoesNotConsumeQuotaWhile429RollsBack() throws Exception {
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) SELECT 'rate-client',password_hash,'Rate client','VIEWER' FROM admin_users WHERE username='viewer'");
        var actor=login("rate-client");Map<String,Object> original=null;
        for(int n=0;n<60;n++) { var event=payload();if(n==0)original=event;assertThat(send(actor,event).getResponse().getStatus()).isEqualTo(201); }
        assertThat(send(actor,original).getResponse().getStatus()).isEqualTo(200);
        var rejected=payload();assertThat(send(actor,rejected).getResponse().getStatus()).isEqualTo(429);assertThat(rows(rejected)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE source='CLIENT' AND actor_id=(SELECT id FROM admin_users WHERE username='rate-client')",Integer.class)).isEqualTo(60);
        clock.now=clock.instant().plusSeconds(60);assertThat(send(actor,payload()).getResponse().getStatus()).isEqualTo(201);
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    @TestConfiguration(proxyBeanMethods=false)
    static class TestClockConfiguration { @Bean @Primary MutableClock clientClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        private volatile Instant now=Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
