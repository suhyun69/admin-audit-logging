package com.example.demo.usecase.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditDetailAppResponse(
    UUID id,
    Instant occurredAt,
    Instant receivedAt,
    String eventType,
    String action,
    Long actorId,
    String actorName,
    String actorRole,
    String source,
    String result,
    String resourceType,
    String resourceId,
    UUID interactionId,
    UUID requestId,
    UUID sessionId,
    String httpMethod,
    String requestPath,
    Integer statusCode,
    Long durationMs,
    String ipAddress,
    String userAgent,
    String errorCode,
    Map<String, Object> metadata,
    List<AuditChangeAppResponse> changes) {}
