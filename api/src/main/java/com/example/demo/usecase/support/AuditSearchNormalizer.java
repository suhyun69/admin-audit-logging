package com.example.demo.usecase.support;

import com.example.demo.audit.domain.AuditEventType;
import com.example.demo.audit.domain.AuditResult;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.common.request.AuditPrivacy;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class AuditSearchNormalizer {
    private final CursorCodec cursors;
    private final Clock clock;
    public AuditSearchNormalizer(CursorCodec cursors,Clock clock) { this.cursors=cursors;this.clock=clock; }
    public NormalizedAuditSearchAppRequest normalize(AuditSearchAppRequest request,Long forcedActor,String action) {
        try {
            Long actor=null;
            if(request.actorId()!=null) {
                if(!request.actorId().matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();
                actor=Long.parseLong(request.actorId());
            }
            if(forcedActor!=null)actor=forcedActor;
            String type=request.eventType()==null?null:AuditEventType.valueOf(request.eventType()).name();
            String result=request.result()==null?null:AuditResult.valueOf(request.result()).name();
            bounded(request.resourceType(),50);bounded(request.resourceId(),100);
            int size=request.size()==null?20:Integer.parseInt(request.size());if(size<1||size>100)throw new IllegalArgumentException();
            var cursor=cursors.audit(request.cursor());
            var interaction=request.interactionId()==null?null:AuditPrivacy.uuid(request.interactionId());
            var requestId=request.requestId()==null?null:AuditPrivacy.uuid(request.requestId());
            Instant anchor=RequestContext.current().map(RequestContext::startedAt).orElseGet(clock::instant);
            Instant to=request.to()==null?anchor:parse(request.to());
            Instant from=request.from()==null?to.minus(Duration.ofHours(24)):parse(request.from());
            validTime(from);validTime(to);
            if(!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0)throw new IllegalArgumentException();
            return new NormalizedAuditSearchAppRequest(actor,type,result,request.resourceType(),request.resourceId(),interaction,requestId,from,to,size,cursor,action);
        } catch(IllegalArgumentException|DateTimeException exception) { throw new ApiException(ErrorCode.VALIDATION_ERROR); }
    }
    private void bounded(String value,int max) { if(value!=null&&(value.isBlank()||value.length()>max))throw new IllegalArgumentException(); }
    private Instant parse(String value) { return Instant.parse(value); }
    private void validTime(Instant value) {
        if(value.isBefore(Instant.parse("0001-01-01T00:00:00Z"))||value.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z")))throw new IllegalArgumentException();
    }
}
