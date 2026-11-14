package com.example.demo.audit;

import com.example.demo.common.request.RequestContext;
import com.example.demo.support.PostgresTestSupport;
import com.example.demo.usecase.AuthUseCase;
import com.example.demo.usecase.LocalAuthUseCase;
import com.example.demo.usecase.dto.LoginAppRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real proxies and PostgreSQL commit failures exercise the advice/transaction order. */
@SpringBootTest
@ActiveProfiles("local")
class AopAuditTransactionTest extends PostgresTestSupport {
    @Autowired AuthUseCase auth;
    @Autowired LocalAuthUseCase local;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE audit_log_changes,audit_logs");
        jdbc.update("DELETE FROM admin_sessions");
        RequestContext.install(new RequestContext(UUID.randomUUID(),UUID.randomUUID(),Instant.now()));
    }
    @AfterEach void cleanup() {
        RequestContext.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS test_session_commit_failure ON admin_sessions");
        jdbc.execute("DROP FUNCTION IF EXISTS test_session_commit_failure()");
    }
    @Test void successfulLoginAuditAppearsOnlyAfterEnclosingCommit() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            auth.login(new LoginAppRequest("admin","local-test-password"));
            assertThat(successCount()).isZero();
        });
        assertThat(successCount()).isEqualTo(1);
        assertThat(sessionCount()).isEqualTo(1);
    }
    @Test void enclosingRollbackDoesNotPublishSuccessfulLoginAudit() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            auth.login(new LoginAppRequest("admin","local-test-password"));
            status.setRollbackOnly();
        });
        assertThat(successCount()).isZero();
        assertThat(sessionCount()).isZero();
    }
    @Test void actualCommitFailureDoesNotPublishNormalOrLocalSuccessfulLoginAudit() {
        jdbc.execute("CREATE FUNCTION test_session_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'deferred session commit failure'; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER test_session_commit_failure AFTER INSERT ON admin_sessions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_session_commit_failure()");
        assertThatThrownBy(()->auth.login(new LoginAppRequest("admin","local-test-password"))).isInstanceOf(RuntimeException.class);
        assertThat(successCount()).isZero();assertThat(sessionCount()).isZero();
        RequestContext.install(new RequestContext(UUID.randomUUID(),UUID.randomUUID(),Instant.now()));
        assertThatThrownBy(()->local.login("admin")).isInstanceOf(RuntimeException.class);
        assertThat(successCount()).isZero();assertThat(sessionCount()).isZero();
    }
    private int successCount() {return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE event_type='AUTH_LOGIN_SUCCESS'",Integer.class);}
    private int sessionCount() {return jdbc.queryForObject("SELECT count(*) FROM admin_sessions",Integer.class);}
}
