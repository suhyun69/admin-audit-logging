package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.common.request.AuditPrivacy;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class AuditDetailUseCaseImpl implements AuditDetailUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditDetailTransactionUseCase reads;
    private final ObserveAuditUseCase failures;
    private final CursorCodec cursors;
    public AuditDetailUseCaseImpl(AuthorizationUseCase authorization,AuditDetailTransactionUseCase reads,ObserveAuditUseCase failures,CursorCodec cursors) {
        this.authorization=authorization;this.reads=reads;this.failures=failures;this.cursors=cursors;
    }
    @Override public AuditDetailAppResponse detail(String value) {
        authorization.requireAuditAdmin();UUID id=AuditPrivacy.uuid(value);
        try { return reads.detail(id); }
        catch(AccessDeniedException|AuthenticationException exception){throw exception;}
        catch(RuntimeException exception){failure("AUDIT_DETAIL","AUDIT_LOG",id,exception);throw exception;}
    }
    @Override public AuditDetailsPageAppResponse interaction(String value,InteractionPageAppRequest request) {
        authorization.requireAuditAdmin();UUID id=AuditPrivacy.uuid(value);int size;
        try { size=request.size()==null?20:Integer.parseInt(request.size());if(size<1||size>100)throw new IllegalArgumentException(); }
        catch(IllegalArgumentException exception){throw new ApiException(ErrorCode.VALIDATION_ERROR);}
        var normalized=new NormalizedInteractionAppRequest(id,size,cursors.audit(request.cursor()));
        try { return reads.interaction(normalized); }
        catch(AccessDeniedException|AuthenticationException exception){throw exception;}
        catch(RuntimeException exception){failure("AUDIT_TIMELINE","INTERACTION",id,exception);throw exception;}
    }
    private void failure(String action,String resource,UUID id,RuntimeException exception) {
        var code=exception instanceof ApiException api?api.getErrorCode():ErrorCode.INTERNAL_ERROR;
        failures.record(AuditWriteAppRequest.event("DATA_READ",action,"BUSINESS","FAILURE").resource(resource,id.toString(),Map.of(),List.of())
                .http(new AuditHttpAppRequest(null,null,code==ErrorCode.AUDIT_NOT_FOUND?404:500,null,null,null,code.name())));
    }
}
