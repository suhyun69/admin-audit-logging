package com.example.demo.security;

import com.example.demo.usecase.AuthUseCase;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

public class OpaqueSessionAuthenticationProvider implements AuthenticationProvider {
    private final AuthUseCase auth;
    public OpaqueSessionAuthenticationProvider(AuthUseCase auth) { this.auth = auth; }
    @Override public Authentication authenticate(Authentication authentication) {
        return new OpaqueSessionAuthenticationToken(auth.authenticate((String)authentication.getCredentials())
                .orElseThrow(() -> new BadCredentialsException("Invalid session")));
    }
    @Override public boolean supports(Class<?> type) { return OpaqueSessionAuthenticationToken.class.isAssignableFrom(type); }
}
