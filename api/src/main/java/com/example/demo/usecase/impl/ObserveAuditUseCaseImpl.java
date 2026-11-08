package com.example.demo.usecase.impl;

import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.IndependentAuditWriteUseCase;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Observation failures cannot replace the HTTP/security result. Separate bean guarantees REQUIRES_NEW interception. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ObserveAuditUseCaseImpl implements ObserveAuditUseCase {
    private static final Logger log = LoggerFactory.getLogger(ObserveAuditUseCaseImpl.class);
    private final IndependentAuditWriteUseCase writer;
    public ObserveAuditUseCaseImpl(IndependentAuditWriteUseCase writer) { this.writer = writer; }
    @Override public void record(AuditWriteAppRequest request) {
        try { writer.record(request); }
        catch (RuntimeException exception) { log.error("Audit observation persistence failed type={}", exception.getClass().getSimpleName()); }
    }
}
