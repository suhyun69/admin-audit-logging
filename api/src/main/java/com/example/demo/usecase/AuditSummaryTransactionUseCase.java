package com.example.demo.usecase;

import com.example.demo.usecase.dto.AuditSummaryAppResponse;
import com.example.demo.usecase.dto.NormalizedAuditSearchAppRequest;

public interface AuditSummaryTransactionUseCase {
  AuditSummaryAppResponse summary(NormalizedAuditSearchAppRequest request);
}
