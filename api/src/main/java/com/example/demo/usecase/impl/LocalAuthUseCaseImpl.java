package com.example.demo.usecase.impl;

import com.example.demo.auth.application.LocalAuthService;
import com.example.demo.auth.domain.AdminAccount;
import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.LocalAuthUseCase;
import com.example.demo.usecase.dto.ActorAppResponse;
import com.example.demo.usecase.dto.LoginAppResponse;
import com.example.demo.usecase.support.AuditStreamHub;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Profile("local")
@Transactional
public class LocalAuthUseCaseImpl implements LocalAuthUseCase {
  private final LocalAuthService local;
  private final AuditStreamHub streams;

  public LocalAuthUseCaseImpl(LocalAuthService local, AuditStreamHub streams) {
    this.local = local;
    this.streams = streams;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActorAppResponse> accounts() {
    return local.accounts().stream().map(this::actor).toList();
  }

  @Override
  @Audited(Operation.LOGIN)
  public LoginAppResponse login(String username) {
    var issued = local.login(username);
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
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SseEmitter stream(String lastEventId) {
    return streams.subscribeLocalClient(lastEventId);
  }

  private ActorAppResponse actor(AdminAccount account) {
    return new ActorAppResponse(
        account.id(), account.username(), account.displayName(), account.role().name());
  }
}
