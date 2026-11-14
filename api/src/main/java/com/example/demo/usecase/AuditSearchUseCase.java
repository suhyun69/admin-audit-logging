package com.example.demo.usecase;

import com.example.demo.usecase.dto.AuditPageAppResponse;
import com.example.demo.usecase.dto.AuditSearchAppRequest;

public interface AuditSearchUseCase {
  AuditPageAppResponse search(AuditSearchAppRequest request);

  AuditPageAppResponse mine(AuditSearchAppRequest request);
}
