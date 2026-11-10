package com.example.demo.usecase.support;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditChange;
import com.example.demo.common.request.AuditPrivacy;
import com.example.demo.usecase.dto.AuditDetailAppResponse;
import com.example.demo.usecase.dto.AuditChangeAppResponse;
import java.util.List;
import com.example.demo.usecase.dto.AuditItemAppResponse;
import org.springframework.stereotype.Component;
@Component
public class AuditResponseMapper {
    public AuditItemAppResponse item(AuditEvent event) {
        return new AuditItemAppResponse(event.id(),event.occurredAt(),event.receivedAt(),event.eventType().name(),event.action(),event.actorId(),event.actorName(),event.actorRole(),
                event.source().name(),event.result().name(),event.resourceType(),event.resourceId(),event.interactionId(),event.requestId(),pagePath(event));
    }
    private String pagePath(AuditEvent event) {
        if(event.eventType()!=com.example.demo.audit.domain.AuditEventType.PAGE_VIEW)return null;
        Object path=AuditPrivacy.metadata(event.metadata()).get("pagePath");
        return path instanceof String value?value:null;
    }
    public AuditDetailAppResponse detail(AuditEvent event,List<AuditChange> changes) {
        return new AuditDetailAppResponse(event.id(),event.occurredAt(),event.receivedAt(),event.eventType().name(),event.action(),event.actorId(),event.actorName(),event.actorRole(),
                event.source().name(),event.result().name(),event.resourceType(),event.resourceId(),event.interactionId(),event.requestId(),event.sessionId(),event.httpMethod(),
                event.requestPath()==null?null:AuditPrivacy.requestPath(event.requestPath()),event.statusCode(),event.durationMs(),event.ipAddress(),AuditPrivacy.userAgent(event.userAgent()),safeErrorCode(event.errorCode()),
                AuditPrivacy.metadata(event.metadata()),changes.stream().map(change->new AuditChangeAppResponse(change.fieldName(),change.beforeValue(),change.afterValue())).toList());
    }

    private String safeErrorCode(String code) {
        if(code==null)return null;
        try { return com.example.demo.common.exception.ErrorCode.valueOf(code).name(); }
        catch(IllegalArgumentException exception) { return null; }
    }

}
