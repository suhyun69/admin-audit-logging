package com.example.demo.usecase;

import com.example.demo.usecase.dto.AuditDetailAppResponse;
import com.example.demo.usecase.dto.AuditDetailsPageAppResponse;
import com.example.demo.usecase.dto.NormalizedInteractionAppRequest;
import java.util.UUID;

public interface AuditDetailTransactionUseCase {
  AuditDetailAppResponse detail(UUID id);

  AuditDetailsPageAppResponse interaction(NormalizedInteractionAppRequest request);
}
