package com.example.demo.usecase;

import com.example.demo.usecase.dto.AuditPageAppResponse;
import com.example.demo.usecase.dto.NormalizedAuditSearchAppRequest;

public interface AuditSearchTransactionUseCase {
  AuditPageAppResponse search(NormalizedAuditSearchAppRequest request);
}
