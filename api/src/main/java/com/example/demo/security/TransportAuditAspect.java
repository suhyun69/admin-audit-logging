package com.example.demo.security;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.*;
import jakarta.servlet.http.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Aspect @Component
public class TransportAuditAspect {
    private final ObserveAuditUseCase observer;
    public TransportAuditAspect(ObserveAuditUseCase observer) {this.observer=observer;}
    @Around("execution(* com.example.demo.security.HttpAuditExecution.execute(..)) && args(request,response,..)")
    public Object http(ProceedingJoinPoint invocation,HttpServletRequest request,HttpServletResponse response) throws Throwable {
        try {return invocation.proceed();}
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
    @AfterReturning("execution(* com.example.demo.common.request.SecurityFailureObserver.failed(..)) && args(code)")
    public void denied(ErrorCode code) {
        if(!RequestContext.current().map(context->context.claimObservation("SECURITY")).orElse(true))return;
        boolean unauthenticated=code==ErrorCode.UNAUTHENTICATED;
        observer.record(AuditWriteAppRequest.event(unauthenticated?"AUTH_UNAUTHENTICATED":"AUTH_ACCESS_DENIED",
                unauthenticated?"AUTHENTICATE":"AUTHORIZE","SECURITY","DENIED")
                .http(new AuditHttpAppRequest(null,null,unauthenticated?401:403,null,null,null,code.name())));
    }
}
