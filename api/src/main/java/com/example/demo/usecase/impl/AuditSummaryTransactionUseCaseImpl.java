package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditQueryService;
import com.example.demo.audit.domain.AuditQuery;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditResponseMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(isolation=Isolation.REPEATABLE_READ)
public class AuditSummaryTransactionUseCaseImpl implements AuditSummaryTransactionUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditQueryService queries;
    private final AuditWriteUseCase audit;
    private final AuditResponseMapper mapper;
    public AuditSummaryTransactionUseCaseImpl(AuthorizationUseCase authorization,AuditQueryService queries,AuditWriteUseCase audit,AuditResponseMapper mapper) {
        this.authorization=authorization;this.queries=queries;this.audit=audit;this.mapper=mapper;
    }
    @Override public AuditSummaryAppResponse summary(NormalizedAuditSearchAppRequest request) {
        var actor=authorization.requireAuthenticated();Long scope="ADMIN".equals(actor.role())?null:actor.id();
        if(scope==null)authorization.requireAuditAdmin();
        if(!Objects.equals(scope,request.actorId()))throw new AccessDeniedException("Access denied");
        var summary=queries.summary(scope,request.from(),request.to());
        var recent=queries.search(new AuditQuery(scope,null,null,null,null,null,null,request.from(),request.to(),10,null)).stream().map(mapper::item).toList();
        var response=new AuditSummaryAppResponse(request.from(),request.to(),summary.totalEvents(),summary.deniedEvents(),summary.errorEvents(),recent);
        audit.record(AuditWriteAppRequest.event("DATA_READ","AUDIT_SUMMARY","BUSINESS","SUCCESS")
                .resource("AUDIT_LOG",null,Map.of("returnedCount",recent.size()),List.of()));return response;
    }
}
