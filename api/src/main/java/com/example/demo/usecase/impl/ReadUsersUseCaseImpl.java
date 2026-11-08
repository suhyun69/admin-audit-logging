package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.LinkedHashMap;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class ReadUsersUseCaseImpl implements ReadUsersUseCase {
    private final AuthorizationUseCase authorization;
    private final ReadUsersTransactionUseCase reads;
    private final ObserveAuditUseCase failures;
    private final CursorCodec cursors;
    public ReadUsersUseCaseImpl(AuthorizationUseCase authorization,ReadUsersTransactionUseCase reads,ObserveAuditUseCase failures,CursorCodec cursors) {
        this.authorization=authorization;this.reads=reads;this.failures=failures;this.cursors=cursors;
    }
    @Override public UsersPageAppResponse list(UserSearchAppRequest request) {
        authorization.requireAuthenticated();
        String q=request.q()==null?"":request.q().trim();
        int size=request.size()==null?20:request.size();
        if(q.length()>100||size<1||size>100)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        if(request.status()!=null)try { UserStatus.valueOf(request.status()); }catch(IllegalArgumentException exception){throw new ApiException(ErrorCode.VALIDATION_ERROR);}
        cursors.user(request.cursor());
        var normalized=new UserSearchAppRequest(q,request.status(),size,request.cursor());
        try { return reads.list(normalized); }
        catch(RuntimeException exception) {
            var metadata=new LinkedHashMap<String,Object>();metadata.put("searched",!q.isEmpty());metadata.put("size",size);
            if(request.status()!=null)metadata.put("status",request.status());
            failures.record(AuditWriteAppRequest.event("DATA_READ","USER_LIST","BUSINESS","FAILURE").resource("USER",null,metadata,List.of()));
            throw exception;
        }
    }
    @Override public UserAppResponse detail(long id) {
        authorization.requireAuthenticated();
        if(id<=0)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        try { return reads.detail(id); }
        catch(RuntimeException exception) {
            String error=exception instanceof ApiException api?api.getErrorCode().name():"INTERNAL_ERROR";
            failures.record(AuditWriteAppRequest.event("DATA_READ","USER_DETAIL","BUSINESS","FAILURE").resource("USER",Long.toString(id),java.util.Map.of(),List.of())
                    .http(new AuditHttpAppRequest(null,null,error.equals("USER_NOT_FOUND")?404:500,null,null,null,error)));
            throw exception;
        }
    }
}
