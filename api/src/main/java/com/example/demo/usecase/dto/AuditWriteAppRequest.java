package com.example.demo.usecase.dto;

import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditWriteAppRequest(UUID id, Instant occurredAt, String eventType, String action,
        String source, String result, ActorSnapshot actor, UUID interactionId, UUID requestId,
        String resourceType, String resourceId, Map<String,Object> metadata,
        List<AuditChangeAppRequest> changes, AuditHttpAppRequest http) {
    public AuditWriteAppRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
    public static AuditWriteAppRequest event(String type, String action, String source, String result) {
        var context = RequestContext.current().orElse(null);
        return new AuditWriteAppRequest(UUID.randomUUID(), Instant.now(), type, action, source, result,
                context == null ? null : context.actor(), context == null ? null : context.interactionId(),
                context == null ? null : context.requestId(), null, null, Map.of(), List.of(), null);
    }
    public AuditWriteAppRequest resource(String type, String id, Map<String,Object> values, List<AuditChangeAppRequest> changes) {
        return new AuditWriteAppRequest(this.id, occurredAt, eventType, action, source, result, actor,
                interactionId, requestId, type, id, values, changes, http);
    }
    public AuditWriteAppRequest http(AuditHttpAppRequest value) {
        return new AuditWriteAppRequest(id, occurredAt, eventType, action, source, result, actor,
                interactionId, requestId, resourceType, resourceId, metadata, changes, value);
    }
}
