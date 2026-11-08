package com.example.demo.usecase.support;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.usecase.dto.AuditItemAppResponse;
import org.springframework.stereotype.Component;
@Component
public class AuditResponseMapper {
    public AuditItemAppResponse item(AuditEvent event) {
        return new AuditItemAppResponse(event.id(),event.occurredAt(),event.receivedAt(),event.eventType().name(),event.action(),event.actorId(),event.actorName(),event.actorRole(),
                event.source().name(),event.result().name(),event.resourceType(),event.resourceId(),event.interactionId(),event.requestId());
    }
}
