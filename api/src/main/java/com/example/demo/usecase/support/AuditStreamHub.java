package com.example.demo.usecase.support;

import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.usecase.AuthUseCase;
import com.example.demo.usecase.dto.AuditItemAppResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-instance, bounded AFTER_COMMIT journal. Never sends network data on the committing thread. */
@Component
public class AuditStreamHub {
    private static final int CAPACITY=1000, MAX_CONNECTIONS=128;
    private final String epoch=UUID.randomUUID().toString();
    private final Deque<Entry> journal=new ArrayDeque<>();
    private final Set<Connection> connections=ConcurrentHashMap.newKeySet();
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final AuthUseCase auth;
    private final AuditResponseMapper mapper;
    private long sequence;
    private final long timeoutMs;
    private final boolean local;
    public AuditStreamHub(AuthUseCase auth,AuditResponseMapper mapper,
            @org.springframework.beans.factory.annotation.Value("${audit.stream.timeout-ms:1800000}") long timeoutMs,
            org.springframework.core.env.Environment environment) {
        this.auth=auth;this.mapper=mapper;this.timeoutMs=timeoutMs;
        this.local=environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"));
    }
    record Entry(long sequence,AuditItemAppResponse item) {}
    record Batch(long latest,boolean reset,List<Entry> entries) {}
    private record Connection(SseEmitter emitter,AtomicBoolean closed) {}

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public synchronized void committed(AuditEvent event) {
        journal.addLast(new Entry(++sequence,mapper.item(event)));
        while(journal.size()>CAPACITY) journal.removeFirst();
    }

    public SseEmitter subscribe(ActorSnapshot actor,String token,String lastEventId) {
        return subscribe(actor,token,lastEventId,false);
    }
    public SseEmitter subscribeLocalClient(String lastEventId) {
        if(!local)throw new ApiException(ErrorCode.ACCESS_DENIED);
        return subscribe(null,null,lastEventId,true);
    }
    private synchronized SseEmitter subscribe(ActorSnapshot actor,String token,String lastEventId,boolean publicClient) {
        if(connections.size()>=MAX_CONNECTIONS) throw new ApiException(ErrorCode.RATE_LIMITED);
        long start=sequence;
        boolean reset=false;
        if(lastEventId!=null&&!lastEventId.isBlank()) {
            try {
                String[] parts=lastEventId.split(":",-1);
                if(parts.length!=2||!epoch.equals(parts[0])||!parts[1].matches("[0-9]{1,19}")) throw new IllegalArgumentException();
                start=Long.parseLong(parts[1]);
                if(unavailable(start)) throw new IllegalArgumentException();
            } catch(IllegalArgumentException exception) { start=sequence;reset=true; }
        }
        var emitter=new SseEmitter(timeoutMs);
        var connection=new Connection(emitter,new AtomicBoolean());
        connections.add(connection);
        Runnable close=()->{ connection.closed().set(true);connections.remove(connection); };
        emitter.onCompletion(close);
        emitter.onTimeout(()->{close.run();emitter.complete();});
        emitter.onError(error->close.run());
        final long initial=start;final boolean initialReset=reset;
        workers.submit(()->pump(connection,actor,token,initial,initialReset,publicClient));
        return emitter;
    }
    private boolean unavailable(long cursor) {
        return cursor<0 || cursor>sequence || (!journal.isEmpty() && cursor<journal.getFirst().sequence()-1);
    }
    private synchronized Batch after(long cursor) {
        if(unavailable(cursor)) return new Batch(sequence,true,List.of());
        return new Batch(sequence,false,journal.stream().filter(entry->entry.sequence()>cursor).toList());
    }
    private String cursor(long value) { return epoch+":"+value; }
    private void send(Connection connection,String event,long position,Object data) throws java.io.IOException {
        connection.emitter().send(SseEmitter.event().name(event).id(cursor(position)).data(data));
    }
    private boolean valid(ActorSnapshot actor,String token) {
        if(token==null)return false;
        return auth.authenticate(token).filter(session->session.sessionId().equals(actor.sessionId())
                &&session.actor().id()==actor.id()&&session.actor().role().equals(actor.role())).isPresent();
    }
    private void pump(Connection connection,ActorSnapshot actor,String token,long position,boolean reset,boolean publicClient) {
        try {
            if(!publicClient&&!valid(actor,token)) { send(connection,"auth-expired",position,Map.of());return; }
            if(reset) send(connection,"reset",position,Map.of("reason","replay-unavailable"));
            send(connection,"ready",position,Map.of());
            long checked=System.nanoTime();
            while(!connection.closed().get()) {
                if(System.nanoTime()-checked>=TimeUnit.SECONDS.toNanos(2)) {
                    if(!publicClient&&!valid(actor,token)) { send(connection,"auth-expired",position,Map.of());break; }
                    connection.emitter().send(SseEmitter.event().id(cursor(position)).comment("heartbeat"));
                    checked=System.nanoTime();
                }
                Batch batch=after(position);
                if(batch.reset()) {
                    position=batch.latest();send(connection,"reset",position,Map.of("reason","replay-unavailable"));
                    send(connection,"ready",position,Map.of());
                } else {
                    for(Entry entry:batch.entries()) {
                        if(connection.closed().get())break;
                        // Recheck before each item, including a slow replay batch.
                        if(!publicClient&&!valid(actor,token)) {
                            send(connection,"auth-expired",position,Map.of());return;
                        }
                        if(publicClient?"CLIENT".equals(entry.item().source()):"ADMIN".equals(actor.role())||Objects.equals(entry.item().actorId(),actor.id())) {
                            send(connection,"audit",entry.sequence(),entry.item());
                        }
                        position=entry.sequence();
                    }
                }
                Thread.sleep(100);
            }
        } catch(InterruptedException exception) { Thread.currentThread().interrupt(); }
        catch(Exception exception) { /* Disconnects and unavailable auth storage close this stream safely. */ }
        finally {
            connection.closed().set(true);connections.remove(connection);connection.emitter().complete();
        }
    }
    @PreDestroy public void close() {
        for(Connection connection:connections) { connection.closed().set(true);connection.emitter().complete(); }
        connections.clear();workers.shutdownNow();
    }
}
