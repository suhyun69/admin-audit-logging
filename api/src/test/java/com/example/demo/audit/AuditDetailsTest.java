package com.example.demo.audit;

import com.example.demo.audit.application.port.out.AuditQueryPort;
import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditDetailsTest extends HttpApiTestSupport {
    @MockitoSpyBean private AuditQueryPort queries;
    private final UUID business=UUID.fromString("80000000-0000-0000-0000-000000000000");
    private final UUID http=UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
    private UUID interaction;
    private Session admin;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");jdbc.update("UPDATE users SET status='ACTIVE',version=0 WHERE id=123");
        interaction=UUID.randomUUID();Instant old=Instant.now().minusSeconds(3*86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(123456000);
        insert("00000000-0000-0000-0000-000000000001","CLIENT","BUTTON_CLICK","ATTEMPTED",old);
        insert("00000000-0000-0000-0000-000000000002","CLIENT","PAGE_VIEW","ATTEMPTED",old);
        insert(http.toString(),"HTTP","HTTP_REQUEST","SUCCESS",old);insert(business.toString(),"BUSINESS","DATA_UPDATE","SUCCESS",old);
        insert("ffffffff-ffff-ffff-ffff-ffffffffffff","SECURITY","AUTH_LOGOUT","SUCCESS",old);
        jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (?,'status','\"ACTIVE\"'::jsonb,'\"SUSPENDED\"'::jsonb)",business);
        jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (?,'email','\"private@example.test\"'::jsonb,'\"other@example.test\"'::jsonb)",business);
        admin=login("admin");
    }
    private void insert(String id,String source,String type,String result,Instant time) {
        jdbc.update("""
                INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,actor_name,actor_role,session_id,interaction_id,request_id,source,
                resource_type,resource_id,http_method,request_path,status_code,duration_ms,result,ip_address,user_agent,metadata)
                VALUES (?,?,?, ?,1,'Server actor','ADMIN',?,?,?,?,'USER','123','PATCH','/api/v1/users/123/status?q=private@example.test',200,10,?,CAST('2001:db8::1' AS inet),?,CAST(? AS jsonb))
                """,UUID.fromString(id),Timestamp.from(time),type,type,UUID.randomUUID(),interaction,UUID.randomUUID(),source,result,"a".repeat(600),"{\"pagePath\":\"/users/123?q=private@example.test\",\"password\":\"private-password\"}");
    }
    @Test void detailFlattensRequestMetadataAndStatusChangesWithoutPrivateLegacyFields() throws Exception {
        var result=mvc.perform(get("/api/v1/audit-logs/"+business).cookie(admin.auth())).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BUSINESS")).andExpect(jsonPath("$.metadata.pagePath").value("/users/123"))
                .andExpect(jsonPath("$.changes.length()").value(1)).andExpect(jsonPath("$.changes[0].fieldName").value("status"))
                .andExpect(jsonPath("$.changes[0].beforeValue").value("ACTIVE")).andExpect(jsonPath("$.changes[0].afterValue").value("SUSPENDED")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private@example.test","private-password","other@example.test");
        var detail=mapper.readTree(result.getResponse().getContentAsString());assertThat(detail.get("requestPath").asText()).isEqualTo("/api/v1/users/123/status");
        assertThat(detail.get("ipAddress").asText()).isEqualTo("2001:db8::1");assertThat(detail.get("userAgent").asText()).hasSize(512);assertThat(detail.get("sessionId").isNull()).isFalse();
    }
    @Test void invalidHistoricalPagePathDoesNotMakeTheRecordUnretrievable() throws Exception {
        jdbc.update("UPDATE audit_logs SET metadata='{"+"\"pagePath\":\"/private/person@example.test\",\"token\":\"private-token\"}'::jsonb,error_code='raw private exception' WHERE id=?",http);
        mvc.perform(get("/api/v1/audit-logs/"+http).cookie(admin.auth())).andExpect(status().isOk()).andExpect(jsonPath("$.metadata").isEmpty()).andExpect(jsonPath("$.errorCode").isEmpty());
    }
    @Test void timelineHasNoImplicitDayLimitAndPaginatesNativeAscendingUuidTies() throws Exception {
        var expected=jdbc.queryForList("SELECT id FROM audit_logs WHERE interaction_id=? ORDER BY occurred_at ASC,id ASC",UUID.class,interaction);var actual=new ArrayList<UUID>();String cursor=null;
        for(int page=0;page<3;page++) {
            var builder=get("/api/v1/audit-logs/interactions/"+interaction).cookie(admin.auth()).param("size","2");if(cursor!=null)builder.param("cursor",cursor);
            var result=mvc.perform(builder).andExpect(status().isOk()).andReturn();var body=mapper.readTree(result.getResponse().getContentAsString());
            for(var item:body.get("items")) { actual.add(UUID.fromString(item.get("id").asText()));if(item.get("id").asText().equals(business.toString()))assertThat(item.get("changes").size()).isEqualTo(1); }
            cursor=body.get("nextCursor").isNull()?null:body.get("nextCursor").asText();assertThat(body.get("hasNext").asBoolean()).isEqualTo(page<2);
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }
    @Test void realClientAndStatusRequestsAreLinkedAcrossAllThreeSources() throws Exception {
        UUID group=UUID.randomUUID();String body=mapper.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"interactionId",group.toString(),"eventType","BUTTON_CLICK","action","USER_SUSPEND_CLICK","resourceType","USER","resourceId","123","pagePath","/users/123","occurredAt",Instant.now().toString()));
        mvc.perform(post("/api/v1/audit-events/client").cookie(admin.auth()).session(admin.csrfSession()).header("X-CSRF-TOKEN",admin.csrfToken()).header("X-Interaction-ID",group.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        var changed=mvc.perform(patch("/api/v1/users/123/status").cookie(admin.auth()).session(admin.csrfSession()).header("X-CSRF-TOKEN",admin.csrfToken()).header("X-Interaction-ID",group.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"version\":0}")).andExpect(status().isOk()).andReturn();
        var result=mvc.perform(get("/api/v1/audit-logs/interactions/"+group).cookie(admin.auth())).andExpect(status().isOk()).andReturn();
        var sources=new HashSet<String>();boolean updateSeen=false;
        for(var item:mapper.readTree(result.getResponse().getContentAsString()).get("items")) {
            sources.add(item.get("source").asText());
            if(item.get("eventType").asText().equals("DATA_UPDATE")) { updateSeen=true;assertThat(item.get("requestId").asText()).isEqualTo(requestId(changed).toString());assertThat(item.get("changes").get(0).get("afterValue").asText()).isEqualTo("SUSPENDED"); }
        }
        assertThat(sources).contains("CLIENT","HTTP","BUSINESS");assertThat(updateSeen).isTrue();
    }
    @Test void repeatableReadKeepsEventAndChangesInOneSnapshotWhenAnotherTransactionCommits() throws Exception {
        doAnswer(invocation->{
            Object original=invocation.callRealMethod();
            var external=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
            external.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (?,'status','\"SUSPENDED\"'::jsonb,'\"ACTIVE\"'::jsonb)",business);
            return original;
        }).when(queries).detail(business);
        mvc.perform(get("/api/v1/audit-logs/"+business).cookie(admin.auth())).andExpect(status().isOk()).andExpect(jsonPath("$.changes.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes WHERE audit_log_id=? AND field_name='status'",Integer.class,business)).isEqualTo(2);
    }
    @Test void missingDetailIsAudited404EmptyInteractionIs200AndUnsupportedFiltersAre400() throws Exception {
        var missing=mvc.perform(get("/api/v1/audit-logs/"+UUID.randomUUID()).cookie(admin.auth())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AUDIT_NOT_FOUND")).andReturn();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND action='AUDIT_DETAIL' AND result='FAILURE'",Integer.class,requestId(missing))).isEqualTo(1);
        mvc.perform(get("/api/v1/audit-logs/interactions/"+UUID.randomUUID()).cookie(admin.auth())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.hasNext").value(false));
        for(String[] param:new String[][]{{"from",Instant.now().toString()},{"size","101"},{"size","0"},{"cursor","invalid"}})
            mvc.perform(get("/api/v1/audit-logs/interactions/"+interaction).cookie(admin.auth()).param(param[0],param[1])).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-logs/1-1-1-1-1").cookie(admin.auth())).andExpect(status().isBadRequest());
    }
    @Test void viewerAndOperatorCannotReadGlobalDetailOrTimeline() throws Exception {
        for(String username:new String[]{"viewer","operator"}) {
            var actor=login(username);
            mvc.perform(get("/api/v1/audit-logs/"+business).cookie(actor.auth())).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/audit-logs/interactions/"+interaction).cookie(actor.auth())).andExpect(status().isForbidden());
        }
    }
}
