package com.example.demo.usecase.dto;
import java.time.Instant;
import java.util.UUID;
public record AuditItemAppResponse(UUID id,Instant occurredAt,Instant receivedAt,String eventType,String action,Long actorId,String actorName,String actorRole,
        String source,String result,String resourceType,String resourceId,UUID interactionId,UUID requestId,String pagePath) {}
