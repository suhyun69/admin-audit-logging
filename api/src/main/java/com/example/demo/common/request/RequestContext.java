package com.example.demo.common.request;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class RequestContext {
    public static final String ATTRIBUTE = RequestContext.class.getName();
    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();
    private final UUID requestId;
    private final UUID interactionId;
    private final Instant startedAt;
    private final long startedNanos;
    private ActorSnapshot actor;
    private final java.util.Set<String> observations = new java.util.HashSet<>();

    public RequestContext(UUID requestId, UUID interactionId, Instant startedAt) {
        this.requestId = requestId;
        this.interactionId = interactionId;
        this.startedAt = startedAt;
        this.startedNanos = System.nanoTime();
    }
    public static Optional<RequestContext> current() { return Optional.ofNullable(CURRENT.get()); }
    public static void install(RequestContext context) { CURRENT.set(context); }
    public static void clear() { CURRENT.remove(); }
    public UUID requestId() { return requestId; }
    public UUID interactionId() { return interactionId; }
    public Instant startedAt() { return startedAt; }
    public long durationMs() { return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000); }
    public boolean claimObservation(String key) { return observations.add(key); }
    public ActorSnapshot actor() { return actor; }
    public void setActor(ActorSnapshot actor) { this.actor = actor; }
}
