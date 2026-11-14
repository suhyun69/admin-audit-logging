package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class ChangeUserStatusUseCaseImpl implements ChangeUserStatusUseCase {
    private final AuthorizationUseCase authorization;
    private final ChangeUserStatusTransactionUseCase changes;
    public ChangeUserStatusUseCaseImpl(AuthorizationUseCase authorization,ChangeUserStatusTransactionUseCase changes) {
        this.authorization=authorization;this.changes=changes;
    }
    @Override public StatusChangeAppResponse change(long id,ChangeUserStatusAppRequest request) {
        authorization.requireUserWrite();
        if(id<=0||request.version()==null||request.version()<0||request.status()==null)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        try { UserStatus.valueOf(request.status()); }catch(IllegalArgumentException exception){throw new ApiException(ErrorCode.VALIDATION_ERROR);}
        return changes.change(id,request);
    }
}
