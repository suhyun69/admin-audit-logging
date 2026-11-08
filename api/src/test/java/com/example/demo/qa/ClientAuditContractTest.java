package com.example.demo.qa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(ClientAuditContractTest.ClockConfiguration.class)
class ClientAuditContractTest extends AuditContractFixture {
    @Autowired AdvancingClock clock;
    @BeforeEach void newRateWindow() { clock.advance(Duration.ofMinutes(2)); }
    private Map<String,Object> payload() {
        var result = new LinkedHashMap<String,Object>();
        result.put("eventId", UUID.randomUUID().toString()); result.put("interactionId", UUID.randomUUID().toString());
        result.put("eventType", "BUTTON_CLICK"); result.put("action", "USER_SUSPEND_CLICK");
        result.put("resourceType", "USER"); result.put("resourceId", "123");
        result.put("pagePath", "/users/123?q=private-contract-query#private-fragment");
        result.put("occurredAt", clock.instant().toString()); return result;
    }
    private MvcResult raw(Actor actor, byte[] body, String interaction) throws Exception {
        return mvc.perform(post("/api/v1/audit-events/client").cookie(actor.cookie()).session(actor.session())
                .header("X-CSRF-TOKEN", actor.csrf()).header("X-Interaction-ID", interaction)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }
    private MvcResult send(Actor actor, Map<String,Object> body) throws Exception {
        return raw(actor, json.writeValueAsBytes(body), body.get("interactionId").toString());
    }
    private UUID id(Map<String,Object> event) { return UUID.fromString(event.get("eventId").toString()); }

    @Test
    void firstReplayAndCanonicalPrivacyPreserveServerIdentityAndOneOriginalRow() throws Exception {
        Actor actor = login("admin"); var event = payload();
        var first = send(actor, event); assertThat(first.getResponse().getStatus()).isEqualTo(201);
        var original = jdbc.queryForMap("SELECT actor_id,session_id,request_id,result,source,metadata::text metadata,metadata->>'pagePath' page FROM audit_logs WHERE id=?", id(event));
        assertThat(original.get("actor_id")).isEqualTo(1L); assertThat(original.get("source")).isEqualTo("CLIENT");
        assertThat(original.get("result")).isEqualTo("ATTEMPTED"); assertThat(original.get("page")).isEqualTo("/users/123");
        assertThat(original.get("request_id")).isEqualTo(requestId(first)); assertThat(original.get("session_id")).isNotNull();
        assertThat(original.get("metadata").toString().contains("private-contract-query")).isFalse();
        var reordered = new LinkedHashMap<String,Object>(); var keys = new ArrayList<>(event.keySet()); Collections.reverse(keys);
        for (String key : keys) reordered.put(key, event.get(key));
        reordered.put("pagePath", "/users/123?q=another-private-query");
        assertThat(send(actor, reordered).getResponse().getStatus()).isEqualTo(200);
        assertThat(rows(id(event))).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT actor_id,session_id,request_id,result,source,metadata::text metadata,metadata->>'pagePath' page FROM audit_logs WHERE id=?", id(event))).isEqualTo(original);
    }

    @Test
    void simultaneousSameIdCommitsOneClientAndRejectsDifferentOwnerPayloadOrServerId() throws Exception {
        Actor actor = login("admin"); var event = payload();
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var workers = Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<Future<Integer>>();
            for (int n = 0; n < 2; n++) tasks.add(workers.submit(() -> {
                ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return send(actor, event).getResponse().getStatus();
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 200);
        } finally { start.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        assertThat(rows(id(event))).isEqualTo(1);
        var changed = new LinkedHashMap<>(event); changed.put("action", "USER_ACTIVATE_CLICK");
        assertThat(send(actor, changed).getResponse().getStatus()).isEqualTo(409);
        var foreign = send(login("viewer"), event); assertThat(foreign.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(foreign.getResponse().getContentAsString()).size()).isEqualTo(3);
        mvc.perform(get("/api/v1/users/123").cookie(actor.cookie())).andExpect(status().isOk());
        for (String source : new String[]{"HTTP", "BUSINESS"}) {
            UUID serverId = jdbc.queryForObject("SELECT id FROM audit_logs WHERE source=? LIMIT 1", UUID.class, source);
            var collision = payload(); collision.put("eventId", serverId.toString());
            assertThat(send(actor, collision).getResponse().getStatus()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT source FROM audit_logs WHERE id=?", String.class, serverId)).isEqualTo(source);
        }
    }

    @Test
    void schemaForgeryDuplicateKeysTrailingJsonAndWrongIdentityAreRejectedWithoutClientRows() throws Exception {
        Actor actor = login("admin");
        for (String key : new String[]{"actorId", "sessionId", "result", "receivedAt", "password"}) {
            var event = payload(); event.put(key, "private-forgery");
            assertThat(send(actor, event).getResponse().getStatus()).isEqualTo(400); assertThat(rows(id(event))).isZero();
        }
        var event = payload(); String body = json.writeValueAsString(event);
        for (String invalid : new String[]{body + " {}", body.substring(0, body.length()-1) + ",\"eventType\":\"PAGE_VIEW\"}"})
            assertThat(raw(actor, invalid.getBytes(StandardCharsets.UTF_8), event.get("interactionId").toString()).getResponse().getStatus()).isEqualTo(400);
        assertThat(raw(actor, json.writeValueAsBytes(event), UUID.randomUUID().toString()).getResponse().getStatus()).isEqualTo(400);
        for (Map<String,Object> override : List.<Map<String,Object>>of(Map.of("eventType", "DATA_UPDATE"), Map.of("resourceId", "124"), Map.of("resourceId", "0"), Map.of("pagePath", "/private/person@example.test"), Map.of("action", 123))) {
            var invalid = payload(); invalid.putAll(override);
            assertThat(send(actor, invalid).getResponse().getStatus()).isEqualTo(400); assertThat(rows(id(invalid))).isZero();
        }
        assertThat(rows(id(event))).isZero();
    }

    @Test
    void rawByteLimitCountsUtf8AndAcceptsExactly8192ButRejects8193() throws Exception {
        Actor actor = login("admin"); var event = payload(); byte[] jsonBytes = json.writeValueAsBytes(event);
        byte[] exact = Arrays.copyOf(jsonBytes, 8192); Arrays.fill(exact, jsonBytes.length, exact.length, (byte)' ');
        assertThat(raw(actor, exact, event.get("interactionId").toString()).getResponse().getStatus()).isEqualTo(201);
        var larger = payload(); jsonBytes = json.writeValueAsBytes(larger);
        byte[] oversized = Arrays.copyOf(jsonBytes, 8193); Arrays.fill(oversized, jsonBytes.length, oversized.length, (byte)' ');
        assertThat(raw(actor, oversized, larger.get("interactionId").toString()).getResponse().getStatus()).isEqualTo(413); assertThat(rows(id(larger))).isZero();
        var utf8 = payload(); utf8.put("pagePath", "/users/123?q=" + "가".repeat(3000));
        assertThat(send(actor, utf8).getResponse().getStatus()).isEqualTo(413); assertThat(rows(id(utf8))).isZero();
    }

    @Test
    void newEventTimeBoundsApplyButOriginalReplaySurvivesTwentyFiveHoursAndRelogin() throws Exception {
        Actor actor = login("admin"); var original = payload();
        assertThat(send(actor, original).getResponse().getStatus()).isEqualTo(201);
        UUID originalSession = jdbc.queryForObject("SELECT session_id FROM audit_logs WHERE id=?", UUID.class, id(original));
        for (Duration offset : List.of(Duration.ofHours(-24), Duration.ofMinutes(5))) {
            var boundary = payload(); boundary.put("occurredAt", clock.instant().plus(offset).toString());
            assertThat(send(actor, boundary).getResponse().getStatus()).isEqualTo(201);
        }
        for (Duration offset : List.of(Duration.ofHours(-24).minusNanos(1), Duration.ofMinutes(5).plusNanos(1))) {
            var outside = payload(); outside.put("occurredAt", clock.instant().plus(offset).toString());
            assertThat(send(actor, outside).getResponse().getStatus()).isEqualTo(400); assertThat(rows(id(outside))).isZero();
        }
        clock.advance(Duration.ofHours(25)); Actor relogged = login("admin");
        assertThat(send(relogged, original).getResponse().getStatus()).isEqualTo(200);
        assertThat(rows(id(original))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT session_id FROM audit_logs WHERE id=?", UUID.class, id(original))).isEqualTo(originalSession);
    }

    @Test
    void sixtyNewIdsConsumeActorQuotaWhileReplayDoesNotAndRejectedInsertRollsBack() throws Exception {
        Actor actor = login("admin"); Map<String,Object> original = null;
        for (int n = 0; n < 60; n++) {
            var event = payload(); if (n == 0) original = event;
            assertThat(send(actor, event).getResponse().getStatus()).isEqualTo(201);
        }
        assertThat(send(actor, original).getResponse().getStatus()).isEqualTo(200);
        var rejected = payload(); assertThat(send(actor, rejected).getResponse().getStatus()).isEqualTo(429); assertThat(rows(id(rejected))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE source='CLIENT' AND actor_id=1", Integer.class)).isEqualTo(60);
        assertThat(send(login("operator"), payload()).getResponse().getStatus()).isEqualTo(201);
        clock.advance(Duration.ofMinutes(2)); assertThat(send(actor, payload()).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void eachAllowedClientTypeAndRealBusinessPatchKeepThreeSourcesCorrelated() throws Exception {
        Actor actor = login("admin");
        for (String[] item : new String[][]{{"PAGE_VIEW", "PAGE_ENTER", "/dashboard"}, {"USER_SEARCH", "USER_SEARCH_SUBMIT", "/users"}, {"FILTER_CHANGE", "AUDIT_FILTER_CHANGE", "/audit-logs/me"}}) {
            var event = payload(); event.put("eventType", item[0]); event.put("action", item[1]); event.put("pagePath", item[2]); event.remove("resourceType"); event.remove("resourceId");
            assertThat(send(actor, event).getResponse().getStatus()).isEqualTo(201);
        }
        var button = payload(); assertThat(send(actor, button).getResponse().getStatus()).isEqualTo(201);
        var patch = mvc.perform(patch("/api/v1/users/123/status").cookie(actor.cookie()).session(actor.session()).header("X-CSRF-TOKEN", actor.csrf())
                .header("X-Interaction-ID", button.get("interactionId")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"version\":0}"))
                .andExpect(status().isOk()).andReturn();
        UUID interaction = UUID.fromString(button.get("interactionId").toString());
        assertThat(jdbc.queryForList("SELECT DISTINCT source FROM audit_logs WHERE interaction_id=?", String.class, interaction)).contains("CLIENT", "HTTP", "BUSINESS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND event_type='DATA_UPDATE' AND result='SUCCESS'", Integer.class, requestId(patch))).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration { @Bean @Primary AdvancingClock contractClock() { return new AdvancingClock(); } }
    static class AdvancingClock extends Clock {
        private volatile Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(30);
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
