package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.AuditDetailAppResponse;
import java.time.Instant;
import java.util.*;
public record AuditDetailWebResponse(UUID id,Instant occurredAt,Instant receivedAt,String eventType,String action,Long actorId,String actorName,String actorRole,
        String source,String result,String resourceType,String resourceId,UUID interactionId,UUID requestId,UUID sessionId,String httpMethod,String requestPath,
        Integer statusCode,Long durationMs,String ipAddress,String userAgent,String errorCode,Map<String,Object> metadata,List<AuditChangeWebResponse> changes) {
    public static AuditDetailWebResponse from(AuditDetailAppResponse event) {
        return new AuditDetailWebResponse(event.id(),event.occurredAt(),event.receivedAt(),event.eventType(),event.action(),event.actorId(),event.actorName(),event.actorRole(),event.source(),event.result(),
                event.resourceType(),event.resourceId(),event.interactionId(),event.requestId(),event.sessionId(),event.httpMethod(),event.requestPath(),event.statusCode(),event.durationMs(),event.ipAddress(),event.userAgent(),
                event.errorCode(),event.metadata(),event.changes().stream().map(AuditChangeWebResponse::from).toList());
    }
}
