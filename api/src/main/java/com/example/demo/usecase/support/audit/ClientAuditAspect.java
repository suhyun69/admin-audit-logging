package com.example.demo.usecase.support.audit;

import com.example.demo.audit.application.ClientAuditService;
import com.example.demo.audit.domain.AuditSource;
import com.example.demo.audit.domain.ClientEventIdentity;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import com.example.demo.usecase.dto.ClientEventAppResponse;
import com.example.demo.usecase.support.AuditRequestMapper;
import com.example.demo.usecase.support.ClientEventRateLimiter;
import java.time.Clock;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** Owns CLIENT persistence, replay identity, concurrent insert and rate-limit rollback policy. */
@Aspect
@Component
public class ClientAuditAspect {
  private final ClientAuditService events;
  private final AuditRequestMapper mapper;
  private final ClientEventRateLimiter rate;
  private final Clock clock;

  public ClientAuditAspect(
      ClientAuditService events,
      AuditRequestMapper mapper,
      ClientEventRateLimiter rate,
      Clock clock) {
    this.events = events;
    this.mapper = mapper;
    this.rate = rate;
    this.clock = clock;
  }

  @Around(
      "execution(* com.example.demo.usecase.support.audit.ClientAuditSubmission.submit(..)) && args(event)")
  public Object collect(ProceedingJoinPoint invocation, AuditWriteAppRequest event)
      throws Throwable {
    String digest = (String) event.metadata().get("payloadDigest");
    var existing = events.identity(event.id());
    if (existing.isPresent()) {
      requireSame(existing.get(), event.actor().id(), digest);
      return new ClientEventAppResponse(event.id(), false);
    }
    Object accepted = invocation.proceed();
    if (events.insertOnce(mapper.event(event))) {
      if (!rate.acquire(event.actor().id(), clock.instant()))
        throw new ApiException(ErrorCode.RATE_LIMITED);
      return accepted;
    }
    requireSame(
        events.identity(event.id()).orElseThrow(() -> new ApiException(ErrorCode.EVENT_CONFLICT)),
        event.actor().id(),
        digest);
    return new ClientEventAppResponse(event.id(), false);
  }

  private void requireSame(ClientEventIdentity existing, long actorId, String digest) {
    if (existing.source() != AuditSource.CLIENT
        || !Objects.equals(existing.actorId(), actorId)
        || !digest.equals(existing.payloadDigest()))
      throw new ApiException(ErrorCode.EVENT_CONFLICT);
  }
}
