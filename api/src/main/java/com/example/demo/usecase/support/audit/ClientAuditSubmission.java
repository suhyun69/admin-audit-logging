package com.example.demo.usecase.support.audit;

import com.example.demo.common.exception.*;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Component;
import java.time.*;

/** Validates the first submission window. Replay lookup in advice intentionally precedes this check. */
@Component
public class ClientAuditSubmission {
    private final Clock clock;
    public ClientAuditSubmission(Clock clock) {this.clock=clock;}
    @Audited(Operation.CLIENT)
    public ClientEventAppResponse submit(AuditWriteAppRequest event) {
        Instant now=clock.instant();
        if(event.occurredAt().isBefore(now.minus(Duration.ofHours(24)))||event.occurredAt().isAfter(now.plus(Duration.ofMinutes(5))))
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return new ClientEventAppResponse(event.id(),true);
    }
}
