package com.example.demo.qa;

import com.example.demo.audit.application.port.out.AuditQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuditQueryContractTest extends AuditContractFixture {
    // Spy coordinates one concurrent DB commit; every query/result remains the real PostgreSQL adapter.
    @MockitoSpyBean AuditQueryPort queries;
    private Instant time;
    @BeforeEach void rangeAnchor() {
        time = Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(123456000);
    }
    private void event(UUID id, long actor, String type, String source, String result, Instant occurred, UUID interaction, UUID request, String metadata, Integer status) {
        jdbc.update("""
                INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result,resource_type,resource_id,interaction_id,request_id,metadata,status_code)
                VALUES (?,?,?,?,?,?,?,'USER','123',?,?,CAST(? AS jsonb),?)
                """, id, Timestamp.from(occurred), type, type, actor, source, result, interaction, request, metadata, status);
    }
    private MockHttpServletRequestBuilder range(String endpoint, Actor actor) {
        return get(endpoint).cookie(actor.cookie()).param("from", time.minusSeconds(1).toString()).param("to", time.plusSeconds(1).toString());
    }
    private List<UUID> ids(MvcResult result, String field) throws Exception {
        var ids = new ArrayList<UUID>();
        for (var item : json.readTree(result.getResponse().getContentAsString()).get(field)) ids.add(UUID.fromString(item.get("id").asText()));
        return ids;
    }

    @Test
    void actualMemberReadSuccessAndMissingFailureAreAuditedWithoutSearchText() throws Exception {
        Actor actor = login("admin");
        var success = mvc.perform(get("/api/v1/users/123").cookie(actor.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(123)).andReturn();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND action='USER_DETAIL' AND result='SUCCESS'", Integer.class, requestId(success))).isEqualTo(1);
        var missing = mvc.perform(get("/api/v1/users/999999").cookie(actor.cookie())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND")).andReturn();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND action='USER_DETAIL' AND result='FAILURE'", Integer.class, requestId(missing))).isEqualTo(1);
        var searched = mvc.perform(get("/api/v1/users").cookie(actor.cookie()).param("q", "  member123@example.test  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andReturn();
        assertThat(jdbc.queryForObject("SELECT metadata->>'searched' FROM audit_logs WHERE request_id=? AND action='USER_LIST'", String.class, requestId(searched))).isEqualTo("true");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE row_to_json(audit_logs)::text LIKE '%member123@example.test%'", Integer.class)).isZero();
    }

    @Test
    void nativeUuidMicrosecondDescPagesHaveNoGapsAndEveryFilterBindsToTheActualRows() throws Exception {
        Actor admin = login("admin"); UUID interaction = UUID.randomUUID(), request = UUID.randomUUID();
        String[] fixed = {"00000000-0000-0000-0000-000000000001", "7fffffff-ffff-ffff-ffff-ffffffffffff", "80000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff", "00000000-0000-0000-0000-000000000002"};
        for (int n = 0; n < fixed.length; n++) event(UUID.fromString(fixed[n]), n % 3 + 1, "DATA_READ", "BUSINESS", "SUCCESS", time, interaction, request, "{}", null);
        var expected = jdbc.queryForList("SELECT id FROM audit_logs WHERE interaction_id=? ORDER BY occurred_at DESC,id DESC", UUID.class, interaction);
        var actual = new ArrayList<UUID>(); String cursor = null;
        for (int page = 0; page < 3; page++) {
            var query = range("/api/v1/audit-logs", admin).param("interactionId", interaction.toString()).param("size", "2");
            if (cursor != null) query.param("cursor", cursor);
            var response = mvc.perform(query).andExpect(status().isOk()).andReturn(); actual.addAll(ids(response, "items"));
            var body = json.readTree(response.getResponse().getContentAsString());
            for (var item : body.get("items")) assertThat(Instant.parse(item.get("occurredAt").asText())).isEqualTo(time);
            assertThat(body.get("hasNext").asBoolean()).isEqualTo(page < 2);
            cursor = body.get("nextCursor").isNull() ? null : body.get("nextCursor").asText();
        }
        assertThat(actual).hasSize(5).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        var filtered = mvc.perform(range("/api/v1/audit-logs", admin).param("actorId", "2").param("eventType", "DATA_READ").param("result", "SUCCESS")
                        .param("resourceType", "USER").param("resourceId", "123").param("interactionId", interaction.toString()).param("requestId", request.toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(ids(filtered, "items")).hasSize(2);
        for (var item : json.readTree(filtered.getResponse().getContentAsString()).get("items")) assertThat(item.get("actorId").asLong()).isEqualTo(2L);
        Actor viewer = login("viewer");
        var mine = mvc.perform(range("/api/v1/audit-logs/me", viewer).param("interactionId", interaction.toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(ids(mine, "items")).hasSize(1);
        assertThat(json.readTree(mine.getResponse().getContentAsString()).get("items").get(0).get("actorId").asLong()).isEqualTo(3L);
        mvc.perform(range("/api/v1/audit-logs/me", viewer).param("interactionId", interaction.toString()).param("actorId", "1")).andExpect(status().isBadRequest());
        mvc.perform(range("/api/v1/audit-logs", admin).param("resourceId", "' OR 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rangesKeepInclusiveExclusiveNanosecondMeaningAndMalformedCursorsAreRejected() throws Exception {
        Actor admin = login("admin"); UUID interaction = UUID.randomUUID();
        event(UUID.randomUUID(), 1, "DATA_READ", "BUSINESS", "SUCCESS", time, interaction, UUID.randomUUID(), "{}", null);
        event(UUID.randomUUID(), 1, "DATA_READ", "BUSINESS", "SUCCESS", time.plusNanos(1000), interaction, UUID.randomUUID(), "{}", null);
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.cookie()).param("from", time.toString()).param("to", time.plusNanos(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.cookie()).param("from", time.plusNanos(1).toString()).param("to", time.plusNanos(2000).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        for (String raw : new String[]{"{}", "{\"occurredAt\":\"" + time + "\",\"id\":\"1-1-1-1-1\"}",
                "{\"occurredAt\":\"" + time.plusNanos(1) + "\",\"id\":\"" + UUID.randomUUID() + "\"}",
                "{\"occurredAt\":\"" + time + "\",\"id\":\"" + UUID.randomUUID() + "\",\"id\":\"" + UUID.randomUUID() + "\"}"}) {
            String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            mvc.perform(range("/api/v1/audit-logs", admin).param("cursor", cursor)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.cookie()).param("from", time.minusSeconds(32L * 86400).toString()).param("to", time.toString())).andExpect(status().isBadRequest());
        mvc.perform(range("/api/v1/audit-logs", admin).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void oldInteractionAscPagesAndDetailStatusChangesStayPrivateAndAdminOnly() throws Exception {
        Actor admin = login("admin"); UUID interaction = UUID.randomUUID();
        Instant old = time.minusSeconds(3L * 86400);
        var expected = new ArrayList<UUID>();
        for (String id : new String[]{"00000000-0000-0000-0000-000000000001", "80000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff"}) {
            UUID eventId = UUID.fromString(id); expected.add(eventId);
            event(eventId, 1, "DATA_UPDATE", "BUSINESS", "SUCCESS", old, interaction, UUID.randomUUID(), "{\"password\":\"private-contract-secret\",\"pagePath\":\"/users/123?q=private-contract-query\"}", 200);
        }
        UUID detail = expected.get(1);
        jdbc.update("UPDATE audit_logs SET request_path='/api/v1/users/123/status?q=private-contract-query',user_agent=?,error_code='private-contract-error' WHERE id=?", "x".repeat(700), detail);
        jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (?,'status','\"ACTIVE\"'::jsonb,'\"SUSPENDED\"'::jsonb)", detail);
        jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (?,'email','\"private-contract-old\"'::jsonb,'\"private-contract-new\"'::jsonb)", detail);
        var response = mvc.perform(get("/api/v1/audit-logs/" + detail).cookie(admin.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1)).andExpect(jsonPath("$.changes[0].fieldName").value("status"))
                .andExpect(jsonPath("$.changes[0].beforeValue").value("ACTIVE")).andExpect(jsonPath("$.changes[0].afterValue").value("SUSPENDED")).andReturn();
        assertThat(response.getResponse().getContentAsString().contains("private-contract")).isFalse();
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("userAgent").asText().length()).isEqualTo(512);
        var actual = new ArrayList<UUID>(); String cursor = null;
        for (int page = 0; page < 2; page++) {
            var query = get("/api/v1/audit-logs/interactions/" + interaction).cookie(admin.cookie()).param("size", "2");
            if (cursor != null) query.param("cursor", cursor);
            var result = mvc.perform(query).andExpect(status().isOk()).andReturn(); actual.addAll(ids(result, "items"));
            var body = json.readTree(result.getResponse().getContentAsString()); assertThat(body.get("hasNext").asBoolean()).isEqualTo(page == 0);
            cursor = body.get("nextCursor").isNull() ? null : body.get("nextCursor").asText();
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        Actor viewer = login("viewer");
        mvc.perform(get("/api/v1/audit-logs/" + detail).cookie(viewer.cookie())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs/interactions/" + interaction).cookie(viewer.cookie())).andExpect(status().isForbidden());
        var missing = mvc.perform(get("/api/v1/audit-logs/" + UUID.randomUUID()).cookie(admin.cookie())).andExpect(status().isNotFound()).andReturn();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND action='AUDIT_DETAIL' AND result='FAILURE'", Integer.class, requestId(missing))).isEqualTo(1);
        mvc.perform(get("/api/v1/audit-logs/interactions/" + UUID.randomUUID()).cookie(admin.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/audit-logs/interactions/" + interaction).cookie(admin.cookie()).param("from", old.toString())).andExpect(status().isBadRequest());
    }

    private void summaryFixture() {
        for (int actor = 1; actor <= 3; actor++) {
            String[] results = {"FAILURE", "SUCCESS", "DENIED", "SUCCESS"};
            Integer[] status = {500, 503, 403, 200};
            for (int n = 0; n < 4; n++) event(UUID.randomUUID(), actor, "HTTP_REQUEST", "HTTP", results[n], time.plusMillis(actor * 10L + n), UUID.randomUUID(), UUID.randomUUID(), "{}", status[n]);
        }
    }

    @Test
    void summaryUsesServerRoleCountsOrErrorsOnceAndActualRecentOrder() throws Exception {
        summaryFixture();
        for (String name : new String[]{"admin", "operator", "viewer"}) {
            Actor actor = login(name); boolean global = name.equals("admin"); long owner = name.equals("operator") ? 2 : 3;
            var result = mvc.perform(range("/api/v1/audit-logs/summary", actor)).andExpect(status().isOk()).andReturn();
            var body = json.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("totalEvents").asLong()).isEqualTo(global ? 12 : 4);
            assertThat(body.get("deniedEvents").asLong()).isEqualTo(global ? 3 : 1);
            assertThat(body.get("errorEvents").asLong()).isEqualTo(global ? 6 : 2);
            assertThat(body.get("recentEvents").size()).isEqualTo(global ? 10 : 4);
            var expected = jdbc.queryForList("SELECT id FROM audit_logs WHERE occurred_at>=? AND occurred_at<?" + (global ? "" : " AND actor_id=" + owner) + " ORDER BY occurred_at DESC,id DESC LIMIT 10", UUID.class, Timestamp.from(time.minusSeconds(1)), Timestamp.from(time.plusSeconds(1)));
            assertThat(ids(result, "recentEvents")).containsExactlyElementsOf(expected);
            if (!global) for (var item : body.get("recentEvents")) assertThat(item.get("actorId").asLong()).isEqualTo(owner);
        }
    }

    @Test
    void summarySnapshotExcludesConcurrentCommitAndItsOwnFutureBoundQuery() throws Exception {
        summaryFixture(); Actor admin = login("admin"); UUID externalId = UUID.randomUUID();
        doAnswer(invocation -> {
            Object actual = invocation.callRealMethod();
            var external = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
            external.update("INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result) VALUES (?,?,'HTTP_REQUEST','HTTP_REQUEST',1,'HTTP','SUCCESS')", externalId, Timestamp.from(time.plusMillis(500)));
            return actual;
        }).when(queries).summary(isNull(), eq(time.minusSeconds(1)), eq(time.plusSeconds(1)));
        var consistent = mvc.perform(range("/api/v1/audit-logs/summary", admin)).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(consistent.getResponse().getContentAsString()).get("totalEvents").asLong()).isEqualTo(12);
        assertThat(ids(consistent, "recentEvents")).doesNotContain(externalId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE occurred_at>=? AND occurred_at<?", Integer.class, Timestamp.from(time.minusSeconds(1)), Timestamp.from(time.plusSeconds(1)))).isEqualTo(13);
        Instant to = Instant.now().plusSeconds(60);
        long before = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE occurred_at>=? AND occurred_at<?", Long.class, Timestamp.from(time.minusSeconds(1)), Timestamp.from(to));
        var self = mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.cookie()).param("from", time.minusSeconds(1).toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(self.getResponse().getContentAsString()).get("totalEvents").asLong()).isEqualTo(before);
        UUID current = requestId(self);
        for (var item : json.readTree(self.getResponse().getContentAsString()).get("recentEvents"))
            if (!item.get("requestId").isNull()) assertThat(item.get("requestId").asText()).isNotEqualTo(current.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=?", Integer.class, current)).isEqualTo(2);
        mvc.perform(range("/api/v1/audit-logs/summary", admin).param("actorId", "3")).andExpect(status().isBadRequest());
        var defaultRange = mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.cookie())).andExpect(status().isOk()).andReturn();
        var body = json.readTree(defaultRange.getResponse().getContentAsString());
        assertThat(java.time.Duration.between(Instant.parse(body.get("from").asText()), Instant.parse(body.get("to").asText()))).isEqualTo(java.time.Duration.ofHours(24));
        var toOnly = mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.cookie()).param("to", time.plusSeconds(1).toString())).andExpect(status().isOk()).andReturn();
        assertThat(Instant.parse(json.readTree(toOnly.getResponse().getContentAsString()).get("from").asText())).isEqualTo(time.plusSeconds(1).minusSeconds(86400));
        var fromOnly = mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.cookie()).param("from", time.toString())).andExpect(status().isOk()).andReturn();
        assertThat(Instant.parse(json.readTree(fromOnly.getResponse().getContentAsString()).get("from").asText())).isEqualTo(time);
    }
}
