package com.example.demo.usecase.impl;

import com.example.demo.user.application.UserService;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ChangeUserStatusTransactionUseCaseImpl implements ChangeUserStatusTransactionUseCase {
    private final AuthorizationUseCase authorization;
    private final UserService users;
    private final AuditWriteUseCase audit;
    public ChangeUserStatusTransactionUseCaseImpl(AuthorizationUseCase authorization,UserService users,AuditWriteUseCase audit) {
        this.authorization=authorization;this.users=users;this.audit=audit;
    }
    @Override public StatusChangeAppResponse change(long id,ChangeUserStatusAppRequest request) {
        authorization.requireUserWrite();
        var result=users.changeStatus(id,request.version(),UserStatus.valueOf(request.status()));
        if(result.changed())audit.record(AuditWriteAppRequest.event("DATA_UPDATE","USER_STATUS_CHANGE","BUSINESS","SUCCESS")
                .resource("USER",Long.toString(id),Map.of("status",result.currentStatus().name()),
                        List.of(new AuditChangeAppRequest("status",result.previousStatus().name(),result.currentStatus().name()))));
        return new StatusChangeAppResponse(id,result.previousStatus().name(),result.currentStatus().name(),result.version());
    }
}
