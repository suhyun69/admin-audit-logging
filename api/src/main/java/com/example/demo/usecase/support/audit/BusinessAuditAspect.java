package com.example.demo.usecase.support.audit;

import com.example.demo.usecase.AuditWriteUseCase;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs inside transaction advice (order 0): audit failure rolls back the business change. */
@Aspect @Component @Order(100)
public class BusinessAuditAspect {
    private final AuditWriteUseCase writer;
    private final BusinessAuditEvents events;
    public BusinessAuditAspect(AuditWriteUseCase writer,BusinessAuditEvents events) {this.writer=writer;this.events=events;}
    @Around("@annotation(audited) && execution(* com.example.demo.usecase.impl.*TransactionUseCaseImpl.*(..))")
    public Object success(ProceedingJoinPoint invocation,Audited audited) throws Throwable {
        Object result=invocation.proceed();
        var event=events.event(audited.value(),invocation.getArgs(),result,null);
        if(event!=null)writer.record(event);
        return result;
    }
}
