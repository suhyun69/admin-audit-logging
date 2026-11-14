package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.common.request.AuditPrivacy;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class AuditDetailUseCaseImpl implements AuditDetailUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditDetailTransactionUseCase reads;
    private final CursorCodec cursors;
    public AuditDetailUseCaseImpl(AuthorizationUseCase authorization,AuditDetailTransactionUseCase reads,CursorCodec cursors) {
        this.authorization=authorization;this.reads=reads;this.cursors=cursors;
    }
    @Override public AuditDetailAppResponse detail(String value) {
        authorization.requireAuditAdmin();UUID id=AuditPrivacy.uuid(value);
        return reads.detail(id);
    }
    @Override public AuditDetailsPageAppResponse interaction(String value,InteractionPageAppRequest request) {
        authorization.requireAuditAdmin();UUID id=AuditPrivacy.uuid(value);int size;
        try { size=request.size()==null?20:Integer.parseInt(request.size());if(size<1||size>100)throw new IllegalArgumentException(); }
        catch(IllegalArgumentException exception){throw new ApiException(ErrorCode.VALIDATION_ERROR);}
        var normalized=new NormalizedInteractionAppRequest(id,size,cursors.audit(request.cursor()));
        return reads.interaction(normalized);
    }
}
