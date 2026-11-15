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
  private final com.example.demo.common.authorization.AuthorizationService permissions;
  private final UserService users;

  public ChangeUserStatusTransactionUseCaseImpl(
      AuthorizationUseCase authorization,
      UserService users,
      com.example.demo.common.authorization.AuthorizationService permissions) {
    this.permissions = permissions;
    this.authorization = authorization;
    this.users = users;
  }

  @Override
  @Audited(Operation.USER_STATUS_CHANGE)
  public StatusChangeAppResponse change(long id, ChangeUserStatusAppRequest request) {
    long actor = authorization.requireAuthenticated().id();
    permissions.lockPolicy();
    permissions.lockResource("user", id);
    if (!permissions.can(actor, "user", "update", id)) {
      throw new org.springframework.security.access.AccessDeniedException("Resource access denied");
    }
    var result = users.changeStatus(id, request.version(), UserStatus.valueOf(request.status()));
    return new StatusChangeAppResponse(
        id, result.previousStatus().name(), result.currentStatus().name(), result.version());
  }
}
