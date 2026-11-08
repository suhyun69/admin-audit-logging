package com.example.demo.security;

import com.example.demo.common.request.ActorSnapshot;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.dto.SessionActorAppResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class OpaqueSessionFilter extends OncePerRequestFilter {
    private final AuthenticationProvider provider;
    public OpaqueSessionFilter(AuthenticationProvider provider) { this.provider = provider; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException, ServletException {
        String token = null;
        if (request.getCookies() != null) for (var cookie : request.getCookies()) if ("AUDIT_SESSION".equals(cookie.getName())) token = cookie.getValue();
        if (token != null) {
            try {
                var authentication = provider.authenticate(new OpaqueSessionAuthenticationToken(token));
                var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
                var session = (SessionActorAppResponse)authentication.getPrincipal();
                var actor = session.actor();
                RequestContext.current().ifPresent(value -> value.setActor(new ActorSnapshot(actor.id(),actor.displayName(),actor.role(),session.sessionId())));
            } catch (AuthenticationException exception) { SecurityContextHolder.clearContext(); }
        }
        try { chain.doFilter(request,response); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
