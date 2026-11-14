package com.example.demo.qa;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Independent HTTP-boundary probe: the business method returns before PostgreSQL rejects COMMIT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AopAuditCommitContractTest extends HttpApiTestSupport {
    @BeforeEach void reset() {
        cleanup();
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");
        jdbc.update("UPDATE users SET status='ACTIVE',version=0 WHERE id=123");
    }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS qa_deferred_audit_failure ON audit_logs");
        jdbc.execute("DROP FUNCTION IF EXISTS qa_deferred_audit_failure()");
    }
    @Test void commitTimeSuccessAuditFailureRollsBackBusinessAndWritesExactlyOneIndependentFailure() throws Exception {
        var actor=login("admin");
        jdbc.execute("CREATE FUNCTION qa_deferred_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='DATA_UPDATE' AND NEW.result='SUCCESS' THEN RAISE EXCEPTION 'qa deferred commit rejection'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER qa_deferred_audit_failure AFTER INSERT ON audit_logs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION qa_deferred_audit_failure()");
        var result=change(actor,"SUSPENDED",0).andExpect(status().isInternalServerError()).andReturn();
        var member=jdbc.queryForMap("SELECT status,version FROM users WHERE id=123");
        assertThat(member.get("status")).isEqualTo("ACTIVE");
        assertThat(member.get("version")).isEqualTo(0L);
        assertThat(count(result,"BUSINESS","SUCCESS")).isZero();
        assertThat(count(result,"BUSINESS","FAILURE")).isEqualTo(1);
        assertThat(count(result,"HTTP","FAILURE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_changes",Integer.class)).isZero();
    }
    @Test void invalidInputStaysOutsideBusinessAuditWhileHttpFailureIsObserved() throws Exception {
        var result=change(login("admin"),"UNRECOGNIZED",0).andExpect(status().isBadRequest()).andReturn();
        assertThat(count(result,"BUSINESS","FAILURE")).isZero();
        assertThat(count(result,"BUSINESS","SUCCESS")).isZero();
        assertThat(count(result,"HTTP","FAILURE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=123",String.class)).isEqualTo("ACTIVE");
    }
    private org.springframework.test.web.servlet.ResultActions change(Session actor,String target,long version) throws Exception {
        return mvc.perform(patch("/api/v1/users/123/status").cookie(actor.auth()).session(actor.csrfSession())
                .header("X-CSRF-TOKEN",actor.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("status",target,"version",version))));
    }
    private int count(MvcResult result,String source,String outcome) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id=? AND source=? AND result=?",Integer.class,requestId(result),source,outcome);
    }
}
