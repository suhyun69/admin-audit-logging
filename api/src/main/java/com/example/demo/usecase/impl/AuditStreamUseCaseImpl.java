package com.example.demo.usecase.impl;

import com.example.demo.usecase.AuditStreamUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.support.AuditStreamHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AuditStreamUseCaseImpl implements AuditStreamUseCase {
    private final AuthorizationUseCase authorization;
    private final AuditStreamHub hub;
    public AuditStreamUseCaseImpl(AuthorizationUseCase authorization, AuditStreamHub hub) {
        this.authorization=authorization; this.hub=hub;
    }
    public SseEmitter subscribe(String token,String lastEventId) {
        return hub.subscribe(authorization.requireAuthenticated(),token,lastEventId);
    }
}
