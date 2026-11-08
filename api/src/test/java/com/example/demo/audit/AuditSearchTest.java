package com.example.demo.audit;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditSearchTest extends HttpApiTestSupport {
    private Instant time;
    private UUID interaction,request;
    private Session admin;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");
        time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(123456000);
        interaction=UUID.randomUUID();request=UUID.randomUUID();
        insert("00000000-0000-0000-0000-000000000001",1,"DATA_READ","SUCCESS","BUSINESS","123",interaction,request,time);
        insert("7fffffff-ffff-ffff-ffff-ffffffffffff",2,"DATA_READ","SUCCESS","BUSINESS","123",interaction,request,time);
        insert("80000000-0000-0000-0000-000000000000",3,"BUTTON_CLICK","ATTEMPTED","CLIENT","123",interaction,request,time);
        insert("ffffffff-ffff-ffff-ffff-ffffffffffff",3,"HTTP_REQUEST","DENIED","HTTP","999",UUID.randomUUID(),UUID.randomUUID(),time);
        insert("00000000-0000-0000-0000-000000000002",1,"DATA_UPDATE","FAILURE","BUSINESS","123",UUID.randomUUID(),UUID.randomUUID(),time);
        admin=login("admin");
    }
    private void insert(String id,int actor,String type,String result,String source,String resource,UUID interaction,UUID request,Instant occurred) {
        jdbc.update("INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,source,result,resource_type,resource_id,interaction_id,request_id) VALUES (?,?,?,?,?,?,?,?,?,?,'USER',?,?,?)",
                UUID.fromString(id),Timestamp.from(occurred),Timestamp.from(occurred),type,type,actor,"Server actor",actor==1?"ADMIN":actor==2?"OPERATOR":"VIEWER",source,result,resource,interaction,request);
    }
    private MockHttpServletRequestBuilder query(String path,Session actor) {
        return get(path).cookie(actor.auth()).param("from",time.minusSeconds(1).toString()).param("to",time.plusSeconds(1).toString());
    }
    @Test void nativeUuidTuplePaginationPreservesMicrosecondsWithoutDuplicatesOrGaps() throws Exception {
        var expected=jdbc.queryForList("SELECT id FROM audit_logs WHERE occurred_at=? ORDER BY occurred_at DESC,id DESC",UUID.class,Timestamp.from(time));
        var actual=new ArrayList<UUID>();String cursor=null;
        for(int page=0;page<3;page++) {
            var builder=query("/api/v1/audit-logs",admin).param("size","2");if(cursor!=null)builder.param("cursor",cursor);
            var result=mvc.perform(builder).andExpect(status().isOk()).andReturn();var json=mapper.readTree(result.getResponse().getContentAsString());
            for(var item:json.get("items")) { actual.add(UUID.fromString(item.get("id").asText()));assertThat(Instant.parse(item.get("occurredAt").asText())).isEqualTo(time); }
            cursor=json.get("nextCursor").isNull()?null:json.get("nextCursor").asText();
            if(page<2) { assertThat(json.get("hasNext").asBoolean()).isTrue();assertThat(new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8)).contains("123456"); }
            else assertThat(json.get("hasNext").asBoolean()).isFalse();
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }
    @Test void everyContractFilterIsBoundAndCombined() throws Exception {
        mvc.perform(query("/api/v1/audit-logs",admin).param("actorId","2").param("eventType","DATA_READ").param("result","SUCCESS")
                .param("resourceType","USER").param("resourceId","123").param("interactionId",interaction.toString()).param("requestId",request.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].actorId").value(2));
        mvc.perform(query("/api/v1/audit-logs",admin).param("result","ATTEMPTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].source").value("CLIENT"));
        mvc.perform(query("/api/v1/audit-logs",admin).param("resourceId","' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }
    @Test void ownActivityCannotBeExpandedByActorFilterAndGlobalSearchIsAdminOnly() throws Exception {
        for(String username:new String[]{"operator","viewer"}) {
            var actor=login(username);long id=username.equals("operator")?2:3;
            var result=mvc.perform(query("/api/v1/audit-logs/me",actor)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(id==2?1:2)).andReturn();
            for(var row:mapper.readTree(result.getResponse().getContentAsString()).get("items"))assertThat(row.get("actorId").asLong()).isEqualTo(id);
            mvc.perform(query("/api/v1/audit-logs",actor)).andExpect(status().isForbidden());
            for(String supplied:new String[]{"1",Long.toString(id),""})mvc.perform(query("/api/v1/audit-logs/me",actor).param("actorId",supplied)).andExpect(status().isBadRequest());
        }
    }
    @Test void fromIsInclusiveToIsExclusiveAndNanosecondBoundariesDoNotRoundIncorrectly() throws Exception {
        var excluded=UUID.randomUUID();insert(excluded.toString(),1,"DATA_READ","SUCCESS","BUSINESS","boundary",interaction,request,time.plusNanos(1000));
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("from",time.toString()).param("to",time.plusNanos(1000).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5));
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("from",time.toString()).param("to",time.plusNanos(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5));
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("from",time.plusNanos(1).toString()).param("to",time.plusNanos(2000).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test void defaultSnapshotExcludesItsOwnQueryAuditAndHttpObservation() throws Exception {
        var response=mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("size","100")).andExpect(status().isOk()).andReturn();
        UUID current=requestId(response);
        for(var item:mapper.readTree(response.getResponse().getContentAsString()).get("items"))if(!item.get("requestId").isNull())assertThat(item.get("requestId").asText()).isNotEqualTo(current.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND action='AUDIT_SEARCH'",Integer.class,current)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM audit_logs WHERE request_id=? AND source='BUSINESS'",String.class,current)).doesNotContain("resourceId","actorId","from","to");
    }
    @Test void rejectsInvalidEnumsSizesIdentifiersRangesAndCursorWithSafe400() throws Exception {
        for(String[] pair:new String[][]{{"actorId","0"},{"actorId","9223372036854775808"},{"eventType","FORGED"},{"result","FORGED"},{"resourceType","x".repeat(51)},
                {"resourceId","x".repeat(101)},{"interactionId","1-1-1-1-1"},{"requestId","bad"},{"size","0"},{"size","101"},{"from","not-a-date"},{"from",time.plusSeconds(2).toString()}})
            mvc.perform(query("/api/v1/audit-logs",admin).with(http->{http.setParameter(pair[0],pair[1]);return http;})).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-logs").cookie(admin.auth()).param("from",time.minusSeconds(32L*86400).toString()).param("to",time.toString())).andExpect(status().isBadRequest());
        mvc.perform(query("/api/v1/audit-logs",admin).param("cursor","bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }
}
