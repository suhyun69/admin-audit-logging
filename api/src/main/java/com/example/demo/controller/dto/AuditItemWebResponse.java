package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.AuditItemAppResponse;
import java.time.Instant;
import java.util.UUID;
public record AuditItemWebResponse(UUID id,Instant occurredAt,Instant receivedAt,String eventType,String action,Long actorId,String actorName,String actorRole,
        String source,String result,String resourceType,String resourceId,UUID interactionId,UUID requestId,String pagePath) {
    public static AuditItemWebResponse from(AuditItemAppResponse event) {
        return new AuditItemWebResponse(event.id(),event.occurredAt(),event.receivedAt(),event.eventType(),event.action(),event.actorId(),event.actorName(),event.actorRole(),event.source(),event.result(),event.resourceType(),event.resourceId(),event.interactionId(),event.requestId(),event.pagePath());
    }
}
