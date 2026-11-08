package com.example.demo.usecase.impl;

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
public class AuditSummaryUseCaseImpl implements AuditSummaryUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditSearchNormalizer normalizer;
    private final AuditSummaryTransactionUseCase reads;
    private final ObserveAuditUseCase failures;
    public AuditSummaryUseCaseImpl(AuthorizationUseCase authorization,AuditSearchNormalizer normalizer,AuditSummaryTransactionUseCase reads,ObserveAuditUseCase failures) {
        this.authorization=authorization;this.normalizer=normalizer;this.reads=reads;this.failures=failures;
    }
    @Override public AuditSummaryAppResponse summary(String from,String to) {
        var actor=authorization.requireAuthenticated();Long scope="ADMIN".equals(actor.role())?null:actor.id();
        if(scope==null)authorization.requireAuditAdmin();
        var normalized=normalizer.normalize(new AuditSearchAppRequest(null,null,null,null,null,null,null,from,to,"10",null),scope,"AUDIT_SUMMARY");
        try { return reads.summary(normalized); }
        catch(AccessDeniedException|AuthenticationException exception) { throw exception; }
        catch(RuntimeException exception) {
            failures.record(AuditWriteAppRequest.event("DATA_READ","AUDIT_SUMMARY","BUSINESS","FAILURE").resource("AUDIT_LOG",null,Map.of(),List.of()));throw exception;
        }
    }
}
