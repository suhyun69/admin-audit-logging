package com.example.demo.audit;

import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.support.HttpApiTestSupport;
import com.example.demo.usecase.AuditWriteUseCase;
import com.example.demo.usecase.IndependentAuditWriteUseCase;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditStreamTest extends HttpApiTestSupport {
    @LocalServerPort int port;
    @Autowired AuditWriteUseCase atomic;
    @Autowired IndependentAuditWriteUseCase independent;
    @Autowired PlatformTransactionManager transactions;
    record Frame(String type,String id,String data) {}
    class Stream implements AutoCloseable {
        final HttpURLConnection connection;
        final BufferedReader reader;
        Stream(Session session,String cursor) throws Exception {this(session,cursor,"/api/v1/audit-logs/stream");}
        Stream(Session session,String cursor,String path) throws Exception {
            connection=(HttpURLConnection)new URI("http://localhost:"+port+path).toURL().openConnection();
            connection.setReadTimeout(5000);connection.setConnectTimeout(5000);
            connection.setRequestProperty("Accept","text/event-stream");
            if(session!=null)connection.setRequestProperty("Cookie",session.auth().getName()+"="+session.auth().getValue());
            if(cursor!=null)connection.setRequestProperty("Last-Event-ID",cursor);
            int status=connection.getResponseCode();
            if(status!=200)connection.disconnect();
            assertThat(status).isEqualTo(200);
            assertThat(connection.getHeaderField("Content-Type")).startsWith("text/event-stream");
            reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8));
        }
        Frame next() throws IOException {
            String type="",id="",data="";
            for(String line;(line=reader.readLine())!=null;) {
                if(line.isEmpty()) { if(!type.isEmpty())return new Frame(type,id,data);continue; }
                if(line.startsWith("event:"))type=line.substring(6).stripLeading();
                if(line.startsWith("id:"))id=line.substring(3).stripLeading();
                if(line.startsWith("data:"))data+=line.substring(5).stripLeading();
            }
            return null;
        }
        public void close() throws IOException { reader.close();connection.disconnect(); }
    }
    private AuditWriteAppRequest event(long actor) {
        return AuditWriteAppRequest.event("DATA_READ","STREAM_TEST","BUSINESS","SUCCESS")
                .actor(new ActorSnapshot(actor,"Actor","ADMIN",UUID.randomUUID()));
    }
    @Test void committedOnlyAndNoHistoryOrReadAuditLoop() throws Exception {
        Session session=login("admin");
        independent.record(event(1));
        try(var stream=new Stream(session,null)) {
            assertThat(stream.next().type()).isEqualTo("ready");
            long before=jdbc.queryForObject("SELECT count(*) FROM audit_logs",Long.class);
            var rolledBack=event(1);
            var committed=event(1);
            new TransactionTemplate(transactions).executeWithoutResult(status->{atomic.record(rolledBack);status.setRollbackOnly();});
            new TransactionTemplate(transactions).executeWithoutResult(status->atomic.record(committed));
            Frame frame=stream.next();assertThat(frame.type()).isEqualTo("audit");
            assertThat(mapper.readTree(frame.data()).get("id").asText()).isEqualTo(committed.id().toString());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Long.class)).isEqualTo(before+1);
        }
    }
    @Test void nonAdminSeesOnlyOwnWhileAdminSeesAll() throws Exception {
        Session admin=login("admin"), viewer=login("viewer");
        try(var all=new Stream(admin,null);var own=new Stream(viewer,null)) {
            all.next();own.next();
            var other=event(1);var mine=event(3);
            independent.record(other);independent.record(mine);
            assertThat(mapper.readTree(all.next().data()).get("id").asText()).isEqualTo(other.id().toString());
            assertThat(mapper.readTree(all.next().data()).get("id").asText()).isEqualTo(mine.id().toString());
            assertThat(mapper.readTree(own.next().data()).get("id").asText()).isEqualTo(mine.id().toString());
        }
    }
    @Test void replaysCommittedMissesUsingJournalCursor() throws Exception {
        Session session=login("admin");String cursor;
        try(var first=new Stream(session,null)) { cursor=first.next().id(); }
        var a=event(1);var b=event(1);independent.record(a);independent.record(b);
        try(var replay=new Stream(session,cursor)) {
            assertThat(replay.next().type()).isEqualTo("ready");
            Frame next=replay.next();assertThat(mapper.readTree(next.data()).get("id").asText()).isEqualTo(a.id().toString());
            assertThat(next.id()).isNotEqualTo(cursor);
            assertThat(mapper.readTree(replay.next().data()).get("id").asText()).isEqualTo(b.id().toString());
        }
    }
    @Test void unknownAndEvictedCursorsExplicitlyReset() throws Exception {
        Session session=login("admin");String cursor;
        try(var first=new Stream(session,null)) { cursor=first.next().id(); }
        new TransactionTemplate(transactions).executeWithoutResult(status->{for(int i=0;i<1001;i++)atomic.record(event(1));});
        for(String invalid:List.of(cursor,"other-server:1","malformed")) {
            try(var reset=new Stream(session,invalid)) {
                assertThat(reset.next().type()).isEqualTo("reset");
                assertThat(reset.next().type()).isEqualTo("ready");
            }
        }
    }
    @Test void revokedSessionClosesEstablishedStreamAndAnonymousIsDenied() throws Exception {
        mvc.perform(get("/api/v1/audit-logs/stream")).andExpect(status().isUnauthorized());
        Session session=login("operator");
        try(var stream=new Stream(session,null)) {
            assertThat(stream.next().type()).isEqualTo("ready");
            jdbc.update("UPDATE admin_sessions SET revoked_at=now() WHERE admin_user_id=2");
            assertThat(stream.next().type()).isEqualTo("auth-expired");
            assertThat(stream.next()).isNull();
        }
    }
    @Test void clientInsertOncePublishesOnlyFirstCommit() throws Exception {
        Session session=login("admin");
        UUID clientId=UUID.randomUUID();
        var payload=Map.of("eventId",clientId.toString(),"interactionId",UUID.randomUUID().toString(),
                "eventType","BUTTON_CLICK","action","USER_SUSPEND_CLICK","resourceType","USER","resourceId","123",
                "pagePath","/users/123","occurredAt",java.time.Instant.now().plusSeconds(120).toString());
        try(var stream=new Stream(session,null)) {
            stream.next();
            for(int expected:new int[]{201,200}) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/audit-events/client")
                        .cookie(session.auth()).session(session.csrfSession()).header("X-CSRF-TOKEN",session.csrfToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(payload)))
                        .andExpect(status().is(expected));
            }
            UUID pastClientId=UUID.randomUUID();
            var past=new HashMap<String,Object>(payload);past.put("eventId",pastClientId.toString());past.put("occurredAt",java.time.Instant.now().minusSeconds(300).toString());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/audit-events/client")
                    .cookie(session.auth()).session(session.csrfSession()).header("X-CSRF-TOKEN",session.csrfToken())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(past)))
                    .andExpect(status().isCreated());
            var marker=event(1);independent.record(marker);
            int clientCount=0;var arrival=new ArrayList<String>();
            for(Frame frame;(frame=stream.next())!=null;) {
                var data=mapper.readTree(frame.data());
                if(data.get("source").asText().equals("CLIENT"))arrival.add(data.get("id").asText());
                if(data.get("id").asText().equals(clientId.toString()))clientCount++;
                if(data.get("id").asText().equals(marker.id().toString()))break;
            }
            assertThat(clientCount).isEqualTo(1);
            assertThat(arrival).containsExactly(clientId.toString(),pastClientId.toString());
        }
    }
    @Test void pageViewStreamIncludesSanitizedPagePathWithoutMetadata() throws Exception {
        Session session=login("admin");
        UUID eventId=UUID.randomUUID();
        var payload=Map.of("eventId",eventId.toString(),"interactionId",UUID.randomUUID().toString(),
                "eventType","PAGE_VIEW","action","PAGE_ENTER","pagePath","/users/123?q=secret#private",
                "occurredAt",java.time.Instant.now().toString());
        try(var stream=new Stream(session,null)) {
            assertThat(stream.next().type()).isEqualTo("ready");
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/audit-events/client")
                    .cookie(session.auth()).session(session.csrfSession()).header("X-CSRF-TOKEN",session.csrfToken())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(payload)))
                    .andExpect(status().isCreated());
            Frame frame=stream.next();
            var data=mapper.readTree(frame.data());
            assertThat(data.get("id").asText()).isEqualTo(eventId.toString());
            assertThat(data.get("pagePath").asText()).isEqualTo("/users/123");
            assertThat(data.has("metadata")).isFalse();
            assertThat(frame.data()).doesNotContain("secret","private","?q=","#");
        }
    }
    @Test void publicLocalStreamShowsOnlyNewClientEventsAndReplaysWithoutAuthentication() throws Exception {
        Session session=login("admin");
        String cursor;
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        try(var stream=new Stream(null,null,"/api/v1/local/audit-logs/stream")) {
            assertThat(stream.next().type()).isEqualTo("ready");
            independent.record(event(1));
            independent.record(AuditWriteAppRequest.event("AUTH_LOGIN_SUCCESS","LOGIN","SECURITY","SUCCESS"));
            var payload=Map.of("eventId",first.toString(),"interactionId",UUID.randomUUID().toString(),
                    "eventType","PAGE_VIEW","action","PAGE_ENTER","pagePath","/users?q=private-search#secret",
                    "occurredAt",java.time.Instant.now().toString());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/audit-events/client")
                    .cookie(session.auth()).session(session.csrfSession()).header("X-CSRF-TOKEN",session.csrfToken())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(payload)))
                    .andExpect(status().isCreated());
            Frame frame=stream.next();var data=mapper.readTree(frame.data());
            assertThat(frame.type()).isEqualTo("audit");assertThat(data.get("source").asText()).isEqualTo("CLIENT");
            assertThat(data.get("id").asText()).isEqualTo(first.toString());
            assertThat(data.get("pagePath").asText()).isEqualTo("/users");
            assertThat(frame.data()).doesNotContain("private-search","secret","metadata","password","token");
            cursor=frame.id();
        }
        independent.record(event(1));
        independent.record(new AuditWriteAppRequest(second,java.time.Instant.now(),"PAGE_VIEW","PAGE_ENTER","CLIENT","ATTEMPTED",
                new ActorSnapshot(1,"Admin","ADMIN",UUID.randomUUID()),null,null,null,null,Map.of("pagePath","/dashboard"),List.of(),null));
        try(var replay=new Stream(null,cursor,"/api/v1/local/audit-logs/stream")) {
            assertThat(replay.next().type()).isEqualTo("ready");
            Frame frame=replay.next();var data=mapper.readTree(frame.data());
            assertThat(data.get("id").asText()).isEqualTo(second.toString());
            assertThat(data.get("source").asText()).isEqualTo("CLIENT");
        }
    }
    @Test void expiredAndDisabledSessionsAreTerminated() throws Exception {
        Session operator=login("operator");
        try(var stream=new Stream(operator,null)) {
            stream.next();jdbc.update("UPDATE admin_sessions SET expires_at=now()-interval '1 second' WHERE admin_user_id=2");
            assertThat(stream.next().type()).isEqualTo("auth-expired");
        }
        Session viewer=login("viewer");
        try(var stream=new Stream(viewer,null)) {
            stream.next();jdbc.update("UPDATE admin_users SET enabled=false WHERE id=3");
            assertThat(stream.next().type()).isEqualTo("auth-expired");
        } finally { jdbc.update("UPDATE admin_users SET enabled=true WHERE id=3"); }
    }
    @Test void connectionLimitIsBoundedAndDisconnectsReleaseSlots() throws Exception {
        Session session=login("admin");
        // Earlier tests may have closed their client sockets just before the next heartbeat observes them.
        Thread.sleep(2300);
        var open=new ArrayList<Stream>();
        try {
            for(int i=0;i<128;i++) {var stream=new Stream(session,null);assertThat(stream.next().type()).isEqualTo("ready");open.add(stream);}
            mvc.perform(get("/api/v1/audit-logs/stream").accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM).cookie(session.auth()))
                    .andExpect(status().isTooManyRequests()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("RATE_LIMITED"));
        } finally { for(Stream stream:open)stream.close(); }
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        AssertionError last=null;
        while(System.nanoTime()<deadline) {
            try(var recovered=new Stream(session,null)) {
                assertThat(recovered.next().type()).isEqualTo("ready");return;
            } catch(AssertionError full) {last=full;Thread.sleep(200);}
        }
        throw new AssertionError("Disconnected subscriptions did not release a connection slot",last);
    }
    @Test void unsupportedFiltersAreRejected() throws Exception {
        Session session=login("admin");
        mvc.perform(get("/api/v1/audit-logs/stream?actorId=2").accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM).cookie(session.auth()))
                .andExpect(status().isBadRequest()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
