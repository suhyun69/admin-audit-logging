package com.example.demo.security;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.RequestContext;
import com.example.demo.common.request.SecurityFailureObserver;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.AuditHttpAppRequest;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditRecorder implements SecurityFailureObserver {
    private final ObserveAuditUseCase observer;
    public SecurityAuditRecorder(ObserveAuditUseCase observer) { this.observer=observer; }
    @Override public void failed(ErrorCode code) {
        if(!RequestContext.current().map(context->context.claimObservation("SECURITY")).orElse(true))return;
        boolean unauthenticated=code==ErrorCode.UNAUTHENTICATED;
        observer.record(AuditWriteAppRequest.event(unauthenticated?"AUTH_UNAUTHENTICATED":"AUTH_ACCESS_DENIED",
                unauthenticated?"AUTHENTICATE":"AUTHORIZE","SECURITY","DENIED")
                .http(new AuditHttpAppRequest(null,null,unauthenticated?401:403,null,null,null,code.name())));
    }
}
