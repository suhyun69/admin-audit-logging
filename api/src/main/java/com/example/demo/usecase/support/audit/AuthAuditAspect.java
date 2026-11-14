package com.example.demo.usecase.support.audit;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.RequestContext;
import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import com.example.demo.usecase.support.AuditAfterCommit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(100)
public class AuthAuditAspect {
  private final AuditAfterCommit afterCommit;
  private final ObserveAuditUseCase observer;

  public AuthAuditAspect(AuditAfterCommit afterCommit, ObserveAuditUseCase observer) {
    this.afterCommit = afterCommit;
    this.observer = observer;
  }

  @Around(
      "@annotation(audited) && (execution(* com.example.demo.usecase.impl.AuthUseCaseImpl.*(..)) || execution(* com.example.demo.usecase.impl.LocalAuthUseCaseImpl.*(..)))")
  public Object observe(ProceedingJoinPoint invocation, Audited audited) throws Throwable {
    Object response;
    try {
      response = invocation.proceed();
    } catch (ApiException exception) {
      if (audited.value() == Operation.LOGIN
          && exception.getErrorCode() == ErrorCode.INVALID_CREDENTIALS
          && claim())
        observer.record(
            AuditWriteAppRequest.event("AUTH_LOGIN_FAILURE", "LOGIN", "SECURITY", "FAILURE")
                .actor(null));
      throw exception;
    }
    if (claim())
      afterCommit.record(
          AuditWriteAppRequest.event(
              audited.value() == Operation.LOGIN ? "AUTH_LOGIN_SUCCESS" : "AUTH_LOGOUT",
              audited.value().name(),
              "SECURITY",
              "SUCCESS"));
    return response;
  }

  private boolean claim() {
    return RequestContext.current()
        .map(context -> context.claimObservation("SECURITY"))
        .orElse(true);
  }
}
