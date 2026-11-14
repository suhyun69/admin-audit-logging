package com.example.demo.usecase.impl;

import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditSearchNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class AuditSummaryUseCaseImpl implements AuditSummaryUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditSearchNormalizer normalizer;
    private final AuditSummaryTransactionUseCase reads;
    public AuditSummaryUseCaseImpl(AuthorizationUseCase authorization,AuditSearchNormalizer normalizer,AuditSummaryTransactionUseCase reads) {
        this.authorization=authorization;this.normalizer=normalizer;this.reads=reads;
    }
    @Override public AuditSummaryAppResponse summary(String from,String to) {
        var actor=authorization.requireAuthenticated();Long scope="ADMIN".equals(actor.role())?null:actor.id();
        if(scope==null)authorization.requireAuditAdmin();
        var normalized=normalizer.normalize(new AuditSearchAppRequest(null,null,null,null,null,null,null,from,to,"10",null),scope,"AUDIT_SUMMARY");
        return reads.summary(normalized);
    }
}
