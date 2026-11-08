package com.example.demo.usecase.support;

import com.example.demo.audit.domain.*;
import com.example.demo.common.request.AuditPrivacy;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class AuditRequestMapper {
    public AuditEvent event(AuditWriteAppRequest request) {
        var actor = request.actor();
        var http = request.http();
        if (request.action() == null || !request.action().matches("[A-Z_]{1,100}"))
            throw new IllegalArgumentException("Invalid audit action");
        return new AuditEvent(request.id() == null ? UUID.randomUUID() : request.id(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt(), Instant.now(),
                AuditEventType.valueOf(request.eventType()), request.action(),
                actor == null ? null : actor.id(), actor == null ? null : actor.name(),
                actor == null ? null : actor.role(), actor == null ? null : actor.sessionId(),
                request.interactionId(), request.requestId(), AuditSource.valueOf(request.source()),
                request.resourceType(), request.resourceId(), http == null ? null : http.method(),
                http == null ? null : AuditPrivacy.requestPath(http.path()), http == null ? null : http.statusCode(),
                http == null ? null : http.durationMs(), AuditResult.valueOf(request.result()),
                http == null ? null : http.ipAddress(), http == null ? null : AuditPrivacy.userAgent(http.userAgent()),
                http == null ? null : http.errorCode(), AuditPrivacy.metadata(request.metadata()));
    }
    public List<AuditChange> changes(AuditWriteAppRequest request) {
        return request.changes().stream().map(change -> new AuditChange(change.fieldName(), change.beforeValue(), change.afterValue())).toList();
    }
}
