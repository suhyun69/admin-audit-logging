package com.example.demo.usecase.impl;

import com.example.demo.auth.application.AuthService;
import com.example.demo.auth.domain.AdminAccount;
import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.AuthUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import org.springframework.security.access.AccessDeniedException;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuthUseCaseImpl implements AuthUseCase {
    private final AuthService auth;
    private final AuthorizationUseCase authorization;
    public AuthUseCaseImpl(AuthService auth,AuthorizationUseCase authorization) { this.auth = auth; this.authorization=authorization; }
    @Override public LoginAppResponse login(LoginAppRequest request) {
        var issued = auth.login(request.username(),request.password());
        var account = issued.account();
        RequestContext.current().ifPresent(context -> context.setActor(new ActorSnapshot(account.id(),account.displayName(),account.role().name(),issued.session().id())));
        return new LoginAppResponse(actor(account),issued.session().id(),issued.token(),issued.session().expiresAt());
    }
    @Override @Transactional(readOnly = true)
    public Optional<SessionActorAppResponse> authenticate(String token) {
        return auth.authenticate(token).map(session -> new SessionActorAppResponse(actor(session.account()),session.session().id()));
    }
    @Override public void logout(UUID sessionId) {
        var actor=authorization.requireAuthenticated();
        if(!actor.sessionId().equals(sessionId))throw new AccessDeniedException("Access denied");
        auth.logout(sessionId);
    }
    private ActorAppResponse actor(AdminAccount account) {
        return new ActorAppResponse(account.id(),account.username(),account.displayName(),account.role().name());
    }
}
