package com.example.demo.security;

import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.AuditHttpAppRequest;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpAuditFilter extends OncePerRequestFilter {
    private final ObserveAuditUseCase observer;
    public HttpAuditFilter(ObserveAuditUseCase observer) { this.observer=observer; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/v1/"); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try { chain.doFilter(request,response); }
        finally {
            if(request.getAttribute(RequestContext.ATTRIBUTE) instanceof RequestContext context) {
                int status=response.getStatus();
                String result=status<400?"SUCCESS":status==401||status==403?"DENIED":"FAILURE";
                observer.record(new AuditWriteAppRequest(UUID.randomUUID(),context.startedAt(),"HTTP_REQUEST","HTTP_REQUEST","HTTP",result,
                        context.actor(),context.interactionId(),context.requestId(),null,null,Map.of(),List.of(),
                        new AuditHttpAppRequest(request.getMethod(),request.getRequestURI(),status,context.durationMs(),request.getRemoteAddr(),request.getHeader("User-Agent"),null)));
            }
        }
    }
}
