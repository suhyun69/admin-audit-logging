package com.example.demo.usecase.support.audit;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import com.example.demo.usecase.dto.ClientEventAppResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Validates the first submission window. Replay lookup in advice intentionally precedes this check.
 */
@Component
public class ClientAuditSubmission {
  private final Clock clock;

  public ClientAuditSubmission(Clock clock) {
    this.clock = clock;
  }

  @Audited(Operation.CLIENT)
  public ClientEventAppResponse submit(AuditWriteAppRequest event) {
    Instant now = clock.instant();
    if (event.occurredAt().isBefore(now.minus(Duration.ofHours(24)))
        || event.occurredAt().isAfter(now.plus(Duration.ofMinutes(5))))
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    return new ClientEventAppResponse(event.id(), true);
  }
}
