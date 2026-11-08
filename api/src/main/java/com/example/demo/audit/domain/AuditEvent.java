package com.example.demo.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(UUID id, Instant occurredAt, Instant receivedAt, AuditEventType eventType,
        String action, Long actorId, String actorName, String actorRole, UUID sessionId,
        UUID interactionId, UUID requestId, AuditSource source, String resourceType, String resourceId,
        String httpMethod, String requestPath, Integer statusCode, Long durationMs, AuditResult result,
        String ipAddress, String userAgent, String errorCode, Map<String,Object> metadata) {
    public AuditEvent { metadata = Map.copyOf(metadata); }
}
