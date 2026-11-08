package com.example.demo.security;

import com.example.demo.usecase.dto.SessionActorAppResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public final class OpaqueSessionAuthenticationToken extends AbstractAuthenticationToken {
    private final SessionActorAppResponse principal;
    private final String credential;
    public OpaqueSessionAuthenticationToken(String raw) { super(List.of()); this.principal = null; this.credential = raw; }
    public OpaqueSessionAuthenticationToken(SessionActorAppResponse principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.actor().role())));
        this.principal = principal; this.credential = null; super.setAuthenticated(true);
    }
    @Override public Object getCredentials() { return credential; }
    @Override public SessionActorAppResponse getPrincipal() { return principal; }
}
