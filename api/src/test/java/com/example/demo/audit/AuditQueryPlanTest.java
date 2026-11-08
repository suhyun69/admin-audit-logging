package com.example.demo.audit;

import com.example.demo.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

/** Representative plan check, not a throughput benchmark or production latency guarantee. */
class AuditQueryPlanTest extends PostgresTestSupport {
    @Test void representativeHundredThousandRowsUseOrderedIndexesForTimeActorTypeAndKeyset() throws Exception {
        var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();var jdbc=new JdbcTemplate(ds);
        jdbc.update("INSERT INTO admin_users(username,password_hash,display_name,role) VALUES ('admin','test-only','Admin','ADMIN'),('operator','test-only','Operator','OPERATOR'),('viewer','test-only','Viewer','VIEWER'),('sparse','test-only','Sparse','VIEWER')");
        jdbc.update("""
                INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result)
                SELECT gen_random_uuid(),TIMESTAMPTZ '2026-11-08T00:00:00Z'-n*interval '1 second',
                    CASE WHEN n%100=0 THEN 'DATA_READ' ELSE 'HTTP_REQUEST' END,
                    CASE WHEN n%100=0 THEN 'USER_DETAIL' ELSE 'HTTP_REQUEST' END,
                    CASE WHEN n%1000=0 THEN 4 ELSE n%3+1 END,CASE WHEN n%100=0 THEN 'BUSINESS' ELSE 'HTTP' END,'SUCCESS'
                FROM generate_series(1,100000) n
                """);
        jdbc.execute("ANALYZE audit_logs");
        var from=Timestamp.from(Instant.parse("2026-11-06T00:00:00Z"));var to=Timestamp.from(Instant.parse("2026-11-08T00:00:00Z"));
        String prefix="EXPLAIN (ANALYZE,BUFFERS) SELECT *,host(ip_address) AS ip_host FROM audit_logs WHERE occurred_at>=? AND occurred_at<?";
        String report="PostgreSQL: "+jdbc.queryForObject("SELECT version()",String.class)+"\nJVM: "+System.getProperty("java.version")+"; "+System.getProperty("os.name")+" "+System.getProperty("os.arch")+"\nRows: 100000; ANALYZE completed; freshly populated isolated database (warm buffers)\n\n";
        String time=String.join("\n",jdbc.queryForList(prefix+" ORDER BY occurred_at DESC,id DESC LIMIT 21",String.class,from,to));
        String denseActor=String.join("\n",jdbc.queryForList(prefix+" AND actor_id=? ORDER BY occurred_at DESC,id DESC LIMIT 21",String.class,from,to,2));
        String actor=String.join("\n",jdbc.queryForList(prefix+" AND actor_id=? ORDER BY occurred_at DESC,id DESC LIMIT 21",String.class,from,to,4));
        String type=String.join("\n",jdbc.queryForList(prefix+" AND event_type=? ORDER BY occurred_at DESC,id DESC LIMIT 21",String.class,from,to,"DATA_READ"));
        var position=jdbc.queryForMap("SELECT occurred_at,id FROM audit_logs WHERE actor_id=4 ORDER BY occurred_at DESC,id DESC OFFSET 20 LIMIT 1");
        String cursor=String.join("\n",jdbc.queryForList(prefix+" AND actor_id=? AND (occurred_at,id)<(?,?) ORDER BY occurred_at DESC,id DESC LIMIT 21",String.class,from,to,4,position.get("occurred_at"),position.get("id")));
        assertThat(time).contains("idx_audit_time");assertThat(actor).contains("idx_audit_actor_time");assertThat(type).contains("idx_audit_type_time");assertThat(cursor).contains("idx_audit_actor_time");
        report+="TIME\n"+time+"\n\nDENSE_ACTOR\n"+denseActor+"\n\nSPARSE_ACTOR\n"+actor+"\n\nTYPE\n"+type+"\n\nKEYSET\n"+cursor+"\n";
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/audit-query-plan.txt"),report);
    }
}
