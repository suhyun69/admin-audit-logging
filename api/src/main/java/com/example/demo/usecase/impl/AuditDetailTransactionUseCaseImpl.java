package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditQueryService;
import com.example.demo.audit.domain.AuditPosition;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditResponseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(isolation=Isolation.REPEATABLE_READ)
public class AuditDetailTransactionUseCaseImpl implements AuditDetailTransactionUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditQueryService queries;
    private final AuditWriteUseCase audit;
    private final AuditResponseMapper mapper;
    private final CursorCodec cursors;
    public AuditDetailTransactionUseCaseImpl(AuthorizationUseCase authorization,AuditQueryService queries,AuditWriteUseCase audit,AuditResponseMapper mapper,CursorCodec cursors) {
        this.authorization=authorization;this.queries=queries;this.audit=audit;this.mapper=mapper;this.cursors=cursors;
    }
    @Override public AuditDetailAppResponse detail(UUID id) {
        authorization.requireAuditAdmin();var event=queries.detail(id);var changes=queries.changes(List.of(id));
        var response=mapper.detail(event,changes.getOrDefault(id,List.of()));
        audit.record(AuditWriteAppRequest.event("DATA_READ","AUDIT_DETAIL","BUSINESS","SUCCESS").resource("AUDIT_LOG",id.toString(),Map.of(),List.of()));return response;
    }
    @Override public AuditDetailsPageAppResponse interaction(NormalizedInteractionAppRequest request) {
        authorization.requireAuditAdmin();var cursor=request.cursor()==null?null:new AuditPosition(request.cursor().occurredAt(),request.cursor().id());
        var found=queries.interaction(request.id(),request.size()+1,cursor);boolean hasNext=found.size()>request.size();var page=found.stream().limit(request.size()).toList();
        var changes=queries.changes(page.stream().map(event->event.id()).toList());
        var items=page.stream().map(event->mapper.detail(event,changes.getOrDefault(event.id(),List.of()))).toList();
        String next=hasNext?cursors.audit(page.get(page.size()-1).occurredAt(),page.get(page.size()-1).id()):null;
        audit.record(AuditWriteAppRequest.event("DATA_READ","AUDIT_TIMELINE","BUSINESS","SUCCESS").resource("INTERACTION",request.id().toString(),Map.of("size",request.size(),"returnedCount",items.size()),List.of()));
        return new AuditDetailsPageAppResponse(items,next,hasNext);
    }
}
