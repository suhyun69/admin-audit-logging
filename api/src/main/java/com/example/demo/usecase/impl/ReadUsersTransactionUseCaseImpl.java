package com.example.demo.usecase.impl;

import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.ReadUsersTransactionUseCase;
import com.example.demo.usecase.dto.UserAppResponse;
import com.example.demo.usecase.dto.UserSearchAppRequest;
import com.example.demo.usecase.dto.UsersPageAppResponse;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import com.example.demo.user.application.UserService;
import com.example.demo.user.domain.User;
import com.example.demo.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReadUsersTransactionUseCaseImpl implements ReadUsersTransactionUseCase {
  private final com.example.demo.common.authorization.AuthorizationService authorization;
  private final UserService users;
  private final CursorCodec cursors;

  public ReadUsersTransactionUseCaseImpl(
      UserService users,
      CursorCodec cursors,
      com.example.demo.common.authorization.AuthorizationService authorization) {
    this.authorization = authorization;
    this.users = users;
    this.cursors = cursors;
  }

  @Override
  @Audited(Operation.USER_LIST)
  public UsersPageAppResponse list(UserSearchAppRequest request) {
    long actor = actor();
    var scope = authorization.getScope(actor, "user", "read");
    boolean all = scope.type() == com.example.demo.common.authorization.AccessScope.ScopeType.ALL;
    var status = request.status() == null ? null : UserStatus.valueOf(request.status());
    var found =
        users.findScoped(
            request.q(),
            status,
            request.size() + 1,
            cursors.user(request.cursor()),
            all,
            scope.groupIds());
    boolean hasNext = found.size() > request.size();
    var selected = found.stream().limit(request.size()).toList();
    var actions =
        authorization.getAllowedActions(
            actor,
            "user",
            selected.stream().map(User::id).toList(),
            java.util.Set.of("read", "update"));
    var page =
        selected.stream()
            .map(u -> response(u, actions.getOrDefault(u.id(), java.util.Set.of())))
            .toList();
    return new UsersPageAppResponse(
        page,
        hasNext ? cursors.user(page.getLast().id()) : null,
        hasNext,
        users.countScoped(request.q(), status, all, scope.groupIds()));
  }

  @Override
  @Audited(Operation.USER_DETAIL)
  public UserAppResponse detail(long id) {
    if (!authorization.can(actor(), "user", "read", id)) {
      throw new org.springframework.security.access.AccessDeniedException("Resource access denied");
    }
    var user = users.detail(id);
    return response(
        user,
        authorization
            .getAllowedActions(
                actor(), "user", java.util.List.of(id), java.util.Set.of("read", "update"))
            .get(id));
  }

  private long actor() {
    return com.example.demo.common.request.RequestContext.current()
        .map(com.example.demo.common.request.RequestContext::actor)
        .orElseThrow()
        .id();
  }

  private UserAppResponse response(User user, java.util.Set<String> actions) {
    return new UserAppResponse(
        user.id(),
        user.email(),
        user.name(),
        user.status().name(),
        user.version(),
        user.createdAt(),
        user.updatedAt(),
        user.groupId(),
        actions);
  }
}
