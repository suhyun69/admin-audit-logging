package com.example.demo.usecase.impl;

import com.example.demo.audit.application.ClientAuditService;
import com.example.demo.audit.domain.AuditSource;
import com.example.demo.audit.domain.ClientEventIdentity;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.AuditPrivacy;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.ClientEventUseCase;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditRequestMapper;
import com.example.demo.usecase.support.ClientEventRateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class ClientEventUseCaseImpl implements ClientEventUseCase {
    private final AuthorizationUseCase authorization;
    private final ClientAuditService events;
    private final AuditRequestMapper mapper;
    private final ClientEventRateLimiter rate;
    private final Clock clock;
    public ClientEventUseCaseImpl(AuthorizationUseCase authorization,ClientAuditService events,AuditRequestMapper mapper,ClientEventRateLimiter rate,Clock clock) {
        this.authorization=authorization;this.events=events;this.mapper=mapper;this.rate=rate;this.clock=clock;
    }
    @Override public ClientEventAppResponse accept(ClientEventAppRequest request) {
        var actor=authorization.requireAuthenticated();
        UUID id=AuditPrivacy.uuid(request.eventId()),interaction=AuditPrivacy.uuid(request.interactionId());
        var context=RequestContext.current().orElseThrow();
        if(context.interactionId()!=null&&!context.interactionId().equals(interaction))throw invalid();
        String page=AuditPrivacy.pagePath(request.pagePath());
        String type=request.resourceType(),resource=request.resourceId();
        if((type==null)!=(resource==null))throw invalid();
        if(type!=null) {
            if(type.equals("USER")) {
                if(resource.length()>19||!resource.matches("[1-9][0-9]*"))throw invalid();
                try { if(Long.parseLong(resource)<=0)throw invalid(); }catch(NumberFormatException exception){throw invalid();}
            } else if(type.equals("AUDIT_LOG"))resource=AuditPrivacy.uuid(resource).toString();
            else throw invalid();
        }
        boolean allowed=switch(request.eventType()) {
            case "PAGE_VIEW" -> "PAGE_ENTER".equals(request.action());
            case "BUTTON_CLICK" -> Set.of("USER_SUSPEND_CLICK","USER_ACTIVATE_CLICK",
                    "USER_SUSPEND_CONFIRM_CLICK","USER_ACTIVATE_CONFIRM_CLICK","USER_STATUS_CANCEL_CLICK","USER_STATUS_CLOSE_CLICK").contains(request.action())&&"USER".equals(type)&&page.equals("/users/"+resource);
            case "USER_SEARCH" -> "USER_SEARCH_SUBMIT".equals(request.action())&&page.equals("/users");
            case "FILTER_CHANGE" -> "USER_STATUS_FILTER".equals(request.action())&&page.equals("/users")
                    ||"AUDIT_FILTER_CHANGE".equals(request.action())&&(page.equals("/audit-logs")||page.equals("/audit-logs/me"));
            default -> false;
        };
        if(!allowed)throw invalid();
        Instant occurred;
        try { occurred=Instant.parse(request.occurredAt()); }catch(DateTimeException exception){throw invalid();}
        // All values are validated to exclude newlines; fixed field order is stable across JVMs and JSON key order.
        String canonical=String.join("\n","v1",Long.toString(actor.id()),id.toString(),interaction.toString(),request.eventType(),request.action(),
                type==null?"":type,resource==null?"":resource,page,occurred.toString());
        String digest=digest(canonical);
        var existing=events.identity(id);
        if(existing.isPresent()) { requireSame(existing.get(),actor.id(),digest);return new ClientEventAppResponse(id,false); }
        Instant now=clock.instant();
        if(occurred.isBefore(now.minus(Duration.ofHours(24)))||occurred.isAfter(now.plus(Duration.ofMinutes(5))))throw invalid();
        var event=mapper.event(new AuditWriteAppRequest(id,occurred,request.eventType(),request.action(),"CLIENT","ATTEMPTED",actor,
                interaction,context.requestId(),type,resource,Map.of("payloadDigest",digest,"pagePath",page),List.of(),null));
        if(events.insertOnce(event)) {
            if(!rate.acquire(actor.id(),now))throw new ApiException(ErrorCode.RATE_LIMITED);
            return new ClientEventAppResponse(id,true);
        }
        requireSame(events.identity(id).orElseThrow(()->new ApiException(ErrorCode.EVENT_CONFLICT)),actor.id(),digest);
        return new ClientEventAppResponse(id,false);
    }
    private void requireSame(ClientEventIdentity existing,long actorId,String digest) {
        if(existing.source()!=AuditSource.CLIENT||!Objects.equals(existing.actorId(),actorId)||!digest.equals(existing.payloadDigest()))throw new ApiException(ErrorCode.EVENT_CONFLICT);
    }
    private ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_ERROR); }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 unavailable");}
    }
}
