package com.example.demo.usecase.impl;

import com.example.demo.auth.application.AuthService;
import com.example.demo.auth.domain.AdminAccount;
import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.AuthUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.support.AuditAfterCommit;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
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
    private final ObserveAuditUseCase observer;
    private final AuditAfterCommit afterCommit;
    public AuthUseCaseImpl(AuthService auth,AuthorizationUseCase authorization,ObserveAuditUseCase observer,AuditAfterCommit afterCommit) {
        this.auth=auth; this.authorization=authorization; this.observer=observer; this.afterCommit=afterCommit;
    }
    @Override public LoginAppResponse login(LoginAppRequest request) {
        com.example.demo.auth.domain.IssuedSession issued;
        try { issued=auth.login(request.username(),request.password()); }
        catch(ApiException exception) {
            if(exception.getErrorCode()==ErrorCode.INVALID_CREDENTIALS && claimSecurity())
                observer.record(AuditWriteAppRequest.event("AUTH_LOGIN_FAILURE","LOGIN","SECURITY","FAILURE").actor(null));
            throw exception;
        }
        var account = issued.account();
        RequestContext.current().ifPresent(context -> context.setActor(new ActorSnapshot(account.id(),account.displayName(),account.role().name(),issued.session().id())));
        if(claimSecurity())afterCommit.record(AuditWriteAppRequest.event("AUTH_LOGIN_SUCCESS","LOGIN","SECURITY","SUCCESS"));
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
        if(claimSecurity())afterCommit.record(AuditWriteAppRequest.event("AUTH_LOGOUT","LOGOUT","SECURITY","SUCCESS"));
    }
    private boolean claimSecurity() { return RequestContext.current().map(context->context.claimObservation("SECURITY")).orElse(true); }
    private ActorAppResponse actor(AdminAccount account) {
        return new ActorAppResponse(account.id(),account.username(),account.displayName(),account.role().name());
    }
}
