package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditSearchNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class AuditSearchUseCaseImpl implements AuditSearchUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditSearchNormalizer normalizer;
    private final AuditSearchTransactionUseCase queries;
    public AuditSearchUseCaseImpl(AuthorizationUseCase authorization,AuditSearchNormalizer normalizer,AuditSearchTransactionUseCase queries) {
        this.authorization=authorization;this.normalizer=normalizer;this.queries=queries;
    }
    @Override public AuditPageAppResponse search(AuditSearchAppRequest request) {
        authorization.requireAuditAdmin();return read(normalizer.normalize(request,null,"AUDIT_SEARCH"));
    }
    @Override public AuditPageAppResponse mine(AuditSearchAppRequest request) {
        var actor=authorization.requireAuthenticated();if(request.actorId()!=null)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return read(normalizer.normalize(request,actor.id(),"AUDIT_ME"));
    }
    private AuditPageAppResponse read(NormalizedAuditSearchAppRequest request) {
        return queries.search(request);
    }
}
