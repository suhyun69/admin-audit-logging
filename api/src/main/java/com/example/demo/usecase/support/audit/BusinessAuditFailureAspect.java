package com.example.demo.usecase.support.audit;

import com.example.demo.usecase.ObserveAuditUseCase;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/** Surrounds transaction advice so independent failure storage starts after rollback. */
@Aspect
@Component
@Order(-100)
public class BusinessAuditFailureAspect {
  private final ObserveAuditUseCase observer;
  private final BusinessAuditEvents events;

  public BusinessAuditFailureAspect(ObserveAuditUseCase observer, BusinessAuditEvents events) {
    this.observer = observer;
    this.events = events;
  }

  @Around(
      "@annotation(audited) && execution(* com.example.demo.usecase.impl.*TransactionUseCaseImpl.*(..))")
  public Object failure(ProceedingJoinPoint invocation, Audited audited) throws Throwable {
    try {
      return invocation.proceed();
    } catch (AccessDeniedException | AuthenticationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      observer.record(events.event(audited.value(), invocation.getArgs(), null, exception));
      throw exception;
    }
  }
}
