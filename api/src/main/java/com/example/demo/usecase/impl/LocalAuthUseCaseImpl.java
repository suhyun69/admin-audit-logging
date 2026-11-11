package com.example.demo.usecase.impl;

import com.example.demo.auth.application.LocalAuthService;
import com.example.demo.auth.domain.AdminAccount;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.LocalAuthUseCase;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.*;
import com.example.demo.usecase.support.AuditAfterCommit;
import com.example.demo.usecase.support.AuditStreamHub;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@Service
@Profile("local")
@Transactional
public class LocalAuthUseCaseImpl implements LocalAuthUseCase {
    private final LocalAuthService local;
    private final ObserveAuditUseCase observer;
    private final AuditAfterCommit afterCommit;
    private final AuditStreamHub streams;
    public LocalAuthUseCaseImpl(LocalAuthService local,ObserveAuditUseCase observer,AuditAfterCommit afterCommit,AuditStreamHub streams) {
        this.local=local;this.observer=observer;this.afterCommit=afterCommit;this.streams=streams;
    }
    @Override @Transactional(readOnly=true)
    public List<ActorAppResponse> accounts() {return local.accounts().stream().map(this::actor).toList();}
    @Override public LoginAppResponse login(String username) {
        com.example.demo.auth.domain.IssuedSession issued;
        try {issued=local.login(username);}
        catch(ApiException exception) {
            if(exception.getErrorCode()==ErrorCode.INVALID_CREDENTIALS&&claimSecurity())
                observer.record(AuditWriteAppRequest.event("AUTH_LOGIN_FAILURE","LOGIN","SECURITY","FAILURE").actor(null));
            throw exception;
        }
        var account=issued.account();
        RequestContext.current().ifPresent(context->context.setActor(new ActorSnapshot(account.id(),account.displayName(),account.role().name(),issued.session().id())));
        if(claimSecurity())afterCommit.record(AuditWriteAppRequest.event("AUTH_LOGIN_SUCCESS","LOGIN","SECURITY","SUCCESS"));
        return new LoginAppResponse(actor(account),issued.session().id(),issued.token(),issued.session().expiresAt());
    }
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public SseEmitter stream(String lastEventId) {return streams.subscribeLocalClient(lastEventId);}
    private boolean claimSecurity() {return RequestContext.current().map(context->context.claimObservation("SECURITY")).orElse(true);}
    private ActorAppResponse actor(AdminAccount account) {return new ActorAppResponse(account.id(),account.username(),account.displayName(),account.role().name());}
}
