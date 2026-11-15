package com.example.demo.usecase.impl;

import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.ChangeUserStatusTransactionUseCase;
import com.example.demo.usecase.dto.ChangeUserStatusAppRequest;
import com.example.demo.usecase.dto.StatusChangeAppResponse;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import com.example.demo.user.application.UserService;
import com.example.demo.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeUserStatusTransactionUseCaseImpl implements ChangeUserStatusTransactionUseCase {
  private final AuthorizationUseCase authorization;
  private final UserService users;

  public ChangeUserStatusTransactionUseCaseImpl(
      AuthorizationUseCase authorization, UserService users) {
    this.authorization = authorization;
    this.users = users;
  }

  @Override
  @Audited(Operation.USER_STATUS_CHANGE)
  public StatusChangeAppResponse change(long id, ChangeUserStatusAppRequest request) {
    authorization.requireUserWrite();
    var result = users.changeStatus(id, request.version(), UserStatus.valueOf(request.status()));
    return new StatusChangeAppResponse(
        id, result.previousStatus().name(), result.currentStatus().name(), result.version());
  }
}
