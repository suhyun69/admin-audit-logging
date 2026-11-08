package com.example.demo.security;

import com.example.demo.common.request.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** A synchronizer token belongs to one opaque authentication session, not merely to an actor. */
public class SessionBoundCsrfTokenRepository implements CsrfTokenRepository {
    private static final String BINDING = SessionBoundCsrfTokenRepository.class.getName()+".binding";
    private final HttpSessionCsrfTokenRepository delegate = new HttpSessionCsrfTokenRepository();
    @Override public CsrfToken generateToken(HttpServletRequest request) { return delegate.generateToken(request); }
    @Override public void saveToken(CsrfToken token,HttpServletRequest request,HttpServletResponse response) {
        delegate.saveToken(token,request,response);
        var session=request.getSession(false);
        if(session!=null) {
            if(token==null) session.removeAttribute(BINDING);
            else session.setAttribute(BINDING,binding());
        }
    }
    @Override public CsrfToken loadToken(HttpServletRequest request) {
        var token=delegate.loadToken(request);
        var session=request.getSession(false);
        return token!=null && session!=null && binding().equals(session.getAttribute(BINDING)) ? token : null;
    }
    private String binding() {
        return RequestContext.current().map(RequestContext::actor)
                .map(actor->actor.sessionId().toString()).orElse("ANONYMOUS");
    }
}
