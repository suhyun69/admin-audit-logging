package com.example.demo.user;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UsersReadTest extends HttpApiTestSupport {
    private Session admin;
    @BeforeEach void reset() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_read_success");
        jdbc.update("DELETE FROM users WHERE id>200");jdbc.update("UPDATE users SET status='ACTIVE'");
        admin=login("admin");
    }
    @AfterEach void cleanup() { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS test_reject_read_success"); }
    @Test
    void idKeysetReturnsEveryMemberWithoutOverlapAndDefaultSizeIsTwenty() throws Exception {
        mvc.perform(get("/api/v1/users").cookie(admin.auth())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20));
        var first=mvc.perform(get("/api/v1/users").cookie(admin.auth()).param("size","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(1)).andExpect(jsonPath("$.hasNext").value(true)).andReturn();
        String cursor=mapper.readTree(first.getResponse().getContentAsString()).get("nextCursor").asText();
        mvc.perform(get("/api/v1/users").cookie(admin.auth()).param("size","100").param("cursor",cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(100))
                .andExpect(jsonPath("$.items[0].id").value(101)).andExpect(jsonPath("$.items[99].id").value(200)).andExpect(jsonPath("$.hasNext").value(false));
    }
    @Test
    void filteredSearchIsTrimmedCaseInsensitiveAndAuditContainsOnlySafeFilters() throws Exception {
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=123");
        var result=mvc.perform(get("/api/v1/users").cookie(admin.auth()).param("q","  member 12  ").param("status","SUSPENDED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(123)).andReturn();
        var row=jdbc.queryForMap("SELECT result,metadata::text metadata,actor_id FROM audit_logs WHERE request_id=? AND source='BUSINESS'",requestId(result));
        assertThat(row.get("result")).isEqualTo("SUCCESS");assertThat(row.get("actor_id")).isEqualTo(1L);
        String metadata=row.get("metadata").toString();
        assertThat(metadata).contains("searched", "true", "SUSPENDED", "returnedCount").doesNotContain("member 12","email","q");
    }
    @Test
    void likeWildcardsAreLiteralSearchCharacters() throws Exception {
        jdbc.update("INSERT INTO users(email,name) VALUES ('percent@example.test','literal%name'),('underscore@example.test','literal_name')");
        mvc.perform(get("/api/v1/users").cookie(admin.auth()).param("q","%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("literal%name"));
        mvc.perform(get("/api/v1/users").cookie(admin.auth()).param("q","_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("literal_name"));
    }
    @Test
    void detailSuccessAndMissingMemberFailureAreAuditedWithSameRequestId() throws Exception {
        var success=mvc.perform(get("/api/v1/users/123").cookie(admin.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(123)).andExpect(jsonPath("$.version").value(0)).andReturn();
        assertThat(jdbc.queryForObject("SELECT result FROM audit_logs WHERE request_id=? AND source='BUSINESS'",String.class,requestId(success))).isEqualTo("SUCCESS");
        var missing=mvc.perform(get("/api/v1/users/999999").cookie(admin.auth()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND")).andReturn();
        var failure=jdbc.queryForMap("SELECT result,error_code,resource_id FROM audit_logs WHERE request_id=? AND source='BUSINESS'",requestId(missing));
        assertThat(failure.get("result")).isEqualTo("FAILURE");assertThat(failure.get("error_code")).isEqualTo("USER_NOT_FOUND");assertThat(failure.get("resource_id")).isEqualTo("999999");
    }
    @Test
    void invalidSearchSizeStatusCursorAndIdentifierReturn400BeforeBusinessRead() throws Exception {
        for(String[] parameter:new String[][]{{"size","0"},{"size","101"},{"status","DELETED"},{"q","a".repeat(101)},{"cursor","not-valid-json"}}) {
            var result=mvc.perform(get("/api/v1/users").cookie(admin.auth()).param(parameter[0],parameter[1]))
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='BUSINESS'",Integer.class,requestId(result))).isZero();
        }
        for(String id:new String[]{"0","-1","abc"})mvc.perform(get("/api/v1/users/"+id).cookie(admin.auth())).andExpect(status().isBadRequest());
    }
    @Test
    void readSuccessAuditFailureRollsBackAndRecordsIndependentFailure() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_read_success CHECK (NOT(event_type='DATA_READ' AND result='SUCCESS'))");
        var result=mvc.perform(get("/api/v1/users/123").cookie(admin.auth())).andExpect(status().isInternalServerError()).andReturn();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='BUSINESS' AND result='SUCCESS'",Integer.class,requestId(result))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source='BUSINESS' AND result='FAILURE'",Integer.class,requestId(result))).isEqualTo(1);
    }
}
