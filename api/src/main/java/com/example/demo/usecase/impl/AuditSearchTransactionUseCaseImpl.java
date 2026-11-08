package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditQueryService;
import com.example.demo.audit.domain.*;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditResponseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AuditSearchTransactionUseCaseImpl implements AuditSearchTransactionUseCase {
    private final AuditQueryService queries;
    private final AuditWriteUseCase audit;
    private final AuthorizationUseCase authorization;
    private final CursorCodec cursors;
    private final AuditResponseMapper mapper;
    public AuditSearchTransactionUseCaseImpl(AuditQueryService queries,AuditWriteUseCase audit,AuthorizationUseCase authorization,CursorCodec cursors,AuditResponseMapper mapper) {
        this.queries=queries;this.audit=audit;this.authorization=authorization;this.cursors=cursors;this.mapper=mapper;
    }
    @Override public AuditPageAppResponse search(NormalizedAuditSearchAppRequest request) {
        if(request.action().equals("AUDIT_ME")) {
            var actor=authorization.requireAuthenticated();
            if(!Long.valueOf(actor.id()).equals(request.actorId()))throw new org.springframework.security.access.AccessDeniedException("Access denied");
        } else authorization.requireAuditAdmin();
        var cursor=request.cursor()==null?null:new AuditPosition(request.cursor().occurredAt(),request.cursor().id());
        var found=queries.search(new AuditQuery(request.actorId(),request.eventType()==null?null:AuditEventType.valueOf(request.eventType()),
                request.result()==null?null:AuditResult.valueOf(request.result()),request.resourceType(),request.resourceId(),request.interactionId(),request.requestId(),request.from(),request.to(),request.size()+1,cursor));
        boolean hasNext=found.size()>request.size();
        var page=found.stream().limit(request.size()).map(mapper::item).toList();
        String next=hasNext?cursors.audit(page.get(page.size()-1).occurredAt(),page.get(page.size()-1).id()):null;
        // Read the snapshot before inserting the audit of this query.
        audit.record(AuditWriteAppRequest.event("DATA_READ",request.action(),"BUSINESS","SUCCESS")
                .resource("AUDIT_LOG",null,Map.of("searched",true,"size",request.size(),"returnedCount",page.size()),List.of()));
        return new AuditPageAppResponse(page,next,hasNext);
    }
}
