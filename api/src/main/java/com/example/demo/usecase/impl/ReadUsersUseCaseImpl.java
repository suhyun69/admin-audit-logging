package com.example.demo.usecase.impl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.ReadUsersTransactionUseCase;
import com.example.demo.usecase.ReadUsersUseCase;
import com.example.demo.usecase.dto.UserAppResponse;
import com.example.demo.usecase.dto.UserSearchAppRequest;
import com.example.demo.usecase.dto.UsersPageAppResponse;
import com.example.demo.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ReadUsersUseCaseImpl implements ReadUsersUseCase {
  private final AuthorizationUseCase authorization;
  private final ReadUsersTransactionUseCase reads;
  private final CursorCodec cursors;

  public ReadUsersUseCaseImpl(
      AuthorizationUseCase authorization, ReadUsersTransactionUseCase reads, CursorCodec cursors) {
    this.authorization = authorization;
    this.reads = reads;
    this.cursors = cursors;
  }

  @Override
  public UsersPageAppResponse list(UserSearchAppRequest request) {
    authorization.requireAuthenticated();
    String q = request.q() == null ? "" : request.q().trim();
    int size = request.size() == null ? 20 : request.size();
    if (q.length() > 100 || size < 1 || size > 100)
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    if (request.status() != null)
      try {
        UserStatus.valueOf(request.status());
      } catch (IllegalArgumentException exception) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR);
      }
    cursors.user(request.cursor());
    var normalized = new UserSearchAppRequest(q, request.status(), size, request.cursor());
    return reads.list(normalized);
  }

  @Override
  public UserAppResponse detail(long id) {
    authorization.requireAuthenticated();
    if (id <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
    return reads.detail(id);
  }
}
