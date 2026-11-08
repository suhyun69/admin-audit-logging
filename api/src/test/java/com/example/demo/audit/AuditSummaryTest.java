package com.example.demo.audit;

import com.example.demo.audit.application.port.out.AuditQueryPort;
import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditSummaryTest extends HttpApiTestSupport {
    @MockitoSpyBean private AuditQueryPort queries;
    private Instant time;
    private Session admin;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_summary_success");
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");
        time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String[] results={"FAILURE","SUCCESS","DENIED","FAILURE","DENIED","SUCCESS","SUCCESS","SUCCESS","FAILURE","DENIED","SUCCESS","SUCCESS"};
        int[] codes={500,500,403,400,403,200,200,200,503,401,200,200};
        for(int n=0;n<12;n++)jdbc.update("INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result,status_code) VALUES (?,?,'HTTP_REQUEST','HTTP_REQUEST',?,'HTTP',?,?)",
                UUID.randomUUID(),Timestamp.from(time.plusMillis(n)),n%3+1,results[n],codes[n]);
        admin=login("admin");
    }
    @AfterEach void cleanup() { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_summary_success"); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder range(Session actor) {
        return get("/api/v1/audit-logs/summary").cookie(actor.auth()).param("from",time.minusSeconds(1).toString()).param("to",time.plusSeconds(1).toString());
    }
    @Test void adminAndOwnScopesUseServerCountsAndTenRecentEventsWithoutDoubleCountingFailure500() throws Exception {
        for(String username:new String[]{"admin","operator","viewer"}) {
            var actor=username.equals("admin")?admin:login(username);
            var response=mvc.perform(range(actor)).andExpect(status().isOk()).andReturn();var body=mapper.readTree(response.getResponse().getContentAsString());
            boolean global=username.equals("admin");assertThat(body.get("totalEvents").asLong()).isEqualTo(global?12:4);
            assertThat(body.get("deniedEvents").asLong()).isEqualTo(global?3:1);assertThat(body.get("errorEvents").asLong()).isEqualTo(global?4:1);
            assertThat(body.get("recentEvents").size()).isEqualTo(global?10:4);
            if(!global)for(var item:body.get("recentEvents"))assertThat(item.get("actorId").asLong()).isEqualTo(username.equals("operator")?2:3);
            assertThat(Instant.parse(body.get("from").asText())).isEqualTo(time.minusSeconds(1));assertThat(Instant.parse(body.get("to").asText())).isEqualTo(time.plusSeconds(1));
            var expected=jdbc.queryForList("SELECT id FROM audit_logs WHERE occurred_at>=? AND occurred_at<?"+(global?"":" AND actor_id="+(username.equals("operator")?2:3))+" ORDER BY occurred_at DESC,id DESC LIMIT 10",UUID.class,Timestamp.from(time.minusSeconds(1)),Timestamp.from(time.plusSeconds(1)));
            var actual=new java.util.ArrayList<UUID>();for(var item:body.get("recentEvents"))actual.add(UUID.fromString(item.get("id").asText()));assertThat(actual).containsExactlyElementsOf(expected);
        }
    }
    @Test void aggregateAndRecentUseSameSnapshotIfAnotherTransactionInsertsBetweenThem() throws Exception {
        UUID externalId=UUID.randomUUID();
        doAnswer(invocation->{
            Object original=invocation.callRealMethod();
            var external=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
            external.update("INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result) VALUES (?,?,'HTTP_REQUEST','HTTP_REQUEST',1,'HTTP','SUCCESS')",externalId,Timestamp.from(time.plusMillis(100)));
            return original;
        }).when(queries).summary(isNull(),any(Instant.class),any(Instant.class));
        var result=mvc.perform(range(admin)).andExpect(status().isOk()).andReturn();var body=mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalEvents").asLong()).isEqualTo(12);assertThat(body.get("recentEvents").size()).isEqualTo(10);
        for(var item:body.get("recentEvents"))assertThat(item.get("id").asText()).isNotEqualTo(externalId.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE occurred_at>=? AND occurred_at<?",Integer.class,Timestamp.from(time.minusSeconds(1)),Timestamp.from(time.plusSeconds(1)))).isEqualTo(13);
    }
    @Test void queryDoesNotCountOrReturnItsOwnAuditEvenWhenUpperBoundIncludesTheQueryTime() throws Exception {
        var from=time.minusSeconds(1);var to=Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        long before=jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE occurred_at>=? AND occurred_at<?",Long.class,Timestamp.from(from),Timestamp.from(to));
        var response=mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth()).param("from",from.toString()).param("to",to.toString())).andExpect(status().isOk()).andReturn();
        var body=mapper.readTree(response.getResponse().getContentAsString());assertThat(body.get("totalEvents").asLong()).isEqualTo(before);
        UUID current=requestId(response);for(var item:body.get("recentEvents"))if(!item.get("requestId").isNull())assertThat(item.get("requestId").asText()).isNotEqualTo(current.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=?",Integer.class,current)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE occurred_at>=? AND occurred_at<?",Long.class,Timestamp.from(from),Timestamp.from(to))).isEqualTo(before+2);
    }
    @Test void partialAndDefaultRangesAreReturnedAndEmptyFutureRangeHasZeroServerCounts() throws Exception {
        var defaults=mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth())).andExpect(status().isOk()).andReturn();var body=mapper.readTree(defaults.getResponse().getContentAsString());
        assertThat(java.time.Duration.between(Instant.parse(body.get("from").asText()),Instant.parse(body.get("to").asText()))).isEqualTo(java.time.Duration.ofHours(24));
        var toOnly=mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth()).param("to",time.plusSeconds(1).toString())).andExpect(status().isOk()).andReturn();body=mapper.readTree(toOnly.getResponse().getContentAsString());
        assertThat(body.get("totalEvents").asLong()).isEqualTo(12);assertThat(Instant.parse(body.get("from").asText())).isEqualTo(time.plusSeconds(1).minusSeconds(86400));
        mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth()).param("from",time.toString())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth()).param("from",Instant.now().plusSeconds(86400).toString()).param("to",Instant.now().plusSeconds(90000).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalEvents").value(0)).andExpect(jsonPath("$.deniedEvents").value(0)).andExpect(jsonPath("$.errorEvents").value(0)).andExpect(jsonPath("$.recentEvents.length()").value(0));
    }
    @Test void rejectsClientActorScopeUnsupportedFiltersBadRangesAndAnonymousAccess() throws Exception {
        for(String[] pair:new String[][]{{"actorId","1"},{"size","10"},{"eventType","HTTP_REQUEST"},{"from","bad"}})
            mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth()).param(pair[0],pair[1])).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-logs/summary").cookie(admin.auth()).param("from",time.minusSeconds(32L*86400).toString()).param("to",time.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-logs/summary")).andExpect(status().isUnauthorized());
    }
    @Test void summaryAuditInsertFailureRollsBackAndLeavesIndependentFailureRecord() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_summary_success CHECK (NOT(action='AUDIT_SUMMARY' AND result='SUCCESS'))");
        var result=mvc.perform(range(admin)).andExpect(status().isInternalServerError()).andReturn();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND action='AUDIT_SUMMARY' AND result='FAILURE'",Integer.class,requestId(result))).isEqualTo(1);
    }
}
