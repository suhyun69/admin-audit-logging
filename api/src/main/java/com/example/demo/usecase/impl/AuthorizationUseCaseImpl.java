package com.example.demo.usecase.impl;

import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.AuthorizationUseCase;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@Transactional(propagation=Propagation.SUPPORTS,readOnly=true)
public class AuthorizationUseCaseImpl implements AuthorizationUseCase {
    @Override public ActorSnapshot requireAuthenticated() { return actor(); }
    @Override public ActorSnapshot requireUserWrite() { return role(Set.of("ADMIN","OPERATOR")); }
    @Override public ActorSnapshot requireAuditAdmin() { return role(Set.of("ADMIN")); }
    @Override public ActorSnapshot requireAdminSettings() { return role(Set.of("ADMIN")); }
    private ActorSnapshot role(Set<String> roles) {
        var actor=actor();
        if(!roles.contains(actor.role()))throw new AccessDeniedException("Access denied");
        return actor;
    }
    private ActorSnapshot actor() {
        return RequestContext.current().map(RequestContext::actor)
                .orElseThrow(()->new AuthenticationCredentialsNotFoundException("Authentication required"));
    }
}
