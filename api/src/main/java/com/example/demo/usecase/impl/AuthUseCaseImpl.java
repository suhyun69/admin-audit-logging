package com.example.demo.usecase.impl;

import com.example.demo.auth.application.AuthService;
import com.example.demo.auth.domain.AdminAccount;
import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.AuthUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.dto.ActorAppResponse;
import com.example.demo.usecase.dto.LoginAppRequest;
import com.example.demo.usecase.dto.LoginAppResponse;
import com.example.demo.usecase.dto.SessionActorAppResponse;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthUseCaseImpl implements AuthUseCase {
  private final AuthService auth;
  private final AuthorizationUseCase authorization;

  public AuthUseCaseImpl(AuthService auth, AuthorizationUseCase authorization) {
    this.auth = auth;
    this.authorization = authorization;
  }

  @Override
  @Audited(Operation.LOGIN)
  public LoginAppResponse login(LoginAppRequest request) {
    var issued = auth.login(request.username(), request.password());
    var account = issued.account();
    RequestContext.current()
        .ifPresent(
            context ->
                context.setActor(
                    new ActorSnapshot(
                        account.id(),
                        account.displayName(),
                        account.role().name(),
                        issued.session().id())));
    return new LoginAppResponse(
        actor(account), issued.session().id(), issued.token(), issued.session().expiresAt());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SessionActorAppResponse> authenticate(String token) {
    return auth.authenticate(token)
        .map(
            session ->
                new SessionActorAppResponse(actor(session.account()), session.session().id()));
  }

  @Override
  @Audited(Operation.LOGOUT)
  public void logout(UUID sessionId) {
    var actor = authorization.requireAuthenticated();
    if (!actor.sessionId().equals(sessionId)) throw new AccessDeniedException("Access denied");
    auth.logout(sessionId);
  }

  private ActorAppResponse actor(AdminAccount account) {
    return new ActorAppResponse(
        account.id(), account.username(), account.displayName(), account.role().name());
  }
}
