package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class ChangeUserStatusUseCaseImpl implements ChangeUserStatusUseCase {
    private final AuthorizationUseCase authorization;
    private final ChangeUserStatusTransactionUseCase changes;
    private final ObserveAuditUseCase failures;
    public ChangeUserStatusUseCaseImpl(AuthorizationUseCase authorization,ChangeUserStatusTransactionUseCase changes,ObserveAuditUseCase failures) {
        this.authorization=authorization;this.changes=changes;this.failures=failures;
    }
    @Override public StatusChangeAppResponse change(long id,ChangeUserStatusAppRequest request) {
        authorization.requireUserWrite();
        if(id<=0||request.version()==null||request.version()<0||request.status()==null)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        try { UserStatus.valueOf(request.status()); }catch(IllegalArgumentException exception){throw new ApiException(ErrorCode.VALIDATION_ERROR);}
        try { return changes.change(id,request); }
        catch(AccessDeniedException|AuthenticationException exception) { throw exception; }
        catch(RuntimeException exception) {
            var code=exception instanceof ApiException api?api.getErrorCode():ErrorCode.INTERNAL_ERROR;
            int status=code==ErrorCode.USER_NOT_FOUND?404:code==ErrorCode.VERSION_CONFLICT?409:500;
            failures.record(AuditWriteAppRequest.event("DATA_UPDATE","USER_STATUS_CHANGE","BUSINESS","FAILURE")
                    .resource("USER",Long.toString(id),Map.of("status",request.status()),List.of())
                    .http(new AuditHttpAppRequest(null,null,status,null,null,null,code.name())));
            throw exception;
        }
    }
}
