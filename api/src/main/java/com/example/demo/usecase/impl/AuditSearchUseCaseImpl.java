package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditSearchNormalizer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class AuditSearchUseCaseImpl implements AuditSearchUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditSearchNormalizer normalizer;
    private final AuditSearchTransactionUseCase queries;
    private final ObserveAuditUseCase failures;
    public AuditSearchUseCaseImpl(AuthorizationUseCase authorization,AuditSearchNormalizer normalizer,AuditSearchTransactionUseCase queries,ObserveAuditUseCase failures) {
        this.authorization=authorization;this.normalizer=normalizer;this.queries=queries;this.failures=failures;
    }
    @Override public AuditPageAppResponse search(AuditSearchAppRequest request) {
        authorization.requireAuditAdmin();return read(normalizer.normalize(request,null,"AUDIT_SEARCH"));
    }
    @Override public AuditPageAppResponse mine(AuditSearchAppRequest request) {
        var actor=authorization.requireAuthenticated();if(request.actorId()!=null)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return read(normalizer.normalize(request,actor.id(),"AUDIT_ME"));
    }
    private AuditPageAppResponse read(NormalizedAuditSearchAppRequest request) {
        try { return queries.search(request); }
        catch(AccessDeniedException|AuthenticationException exception) { throw exception; }
        catch(RuntimeException exception) {
            failures.record(AuditWriteAppRequest.event("DATA_READ",request.action(),"BUSINESS","FAILURE")
                    .resource("AUDIT_LOG",null,Map.of("searched",true,"size",request.size()),List.of()));throw exception;
        }
    }
}
