package com.example.demo.audit;

import com.example.demo.support.HttpApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="audit.stream.timeout-ms=200")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditStreamTimeoutTest extends HttpApiTestSupport {
    @LocalServerPort int port;
    @Test void asyncTimeoutCompletesStreamWithoutJsonErrorOrHttpAudit() throws Exception {
        var session=login("admin");
        long before=jdbc.queryForObject("SELECT count(*) FROM audit_logs",Long.class);
        var connection=(HttpURLConnection)new URI("http://localhost:"+port+"/api/v1/audit-logs/stream").toURL().openConnection();
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Cookie",session.auth().getName()+"="+session.auth().getValue());
        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            String body=new String(connection.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertThat(body).contains("event:ready").doesNotContain("INTERNAL_ERROR","requestId","message");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Long.class)).isEqualTo(before);
        } finally { connection.disconnect(); }
    }
}
