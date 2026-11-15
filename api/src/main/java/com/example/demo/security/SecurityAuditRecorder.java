package com.example.demo.security;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.SecurityFailureObserver;
import org.springframework.stereotype.Component;

/** Security adapters signal rejection here; the aspect owns audit collection. */
@Component
public class SecurityAuditRecorder implements SecurityFailureObserver {
  @Override
  public void failed(ErrorCode code) {}
}
