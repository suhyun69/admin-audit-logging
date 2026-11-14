package com.example.demo.usecase.impl;

import com.example.demo.user.application.UserService;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import com.example.demo.usecase.support.audit.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeUserStatusTransactionUseCaseImpl implements ChangeUserStatusTransactionUseCase {
    private final AuthorizationUseCase authorization;
    private final UserService users;
    public ChangeUserStatusTransactionUseCaseImpl(AuthorizationUseCase authorization,UserService users) {
        this.authorization=authorization;this.users=users;
    }
    @Override @Audited(Operation.USER_STATUS_CHANGE) public StatusChangeAppResponse change(long id,ChangeUserStatusAppRequest request) {
        authorization.requireUserWrite();
        var result=users.changeStatus(id,request.version(),UserStatus.valueOf(request.status()));
        return new StatusChangeAppResponse(id,result.previousStatus().name(),result.currentStatus().name(),result.version());
    }
}
