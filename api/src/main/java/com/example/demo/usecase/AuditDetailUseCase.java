package com.example.demo.usecase;

import com.example.demo.usecase.dto.AuditDetailAppResponse;
import com.example.demo.usecase.dto.AuditDetailsPageAppResponse;
import com.example.demo.usecase.dto.InteractionPageAppRequest;

public interface AuditDetailUseCase {
  AuditDetailAppResponse detail(String id);

  AuditDetailsPageAppResponse interaction(String id, InteractionPageAppRequest request);
}
