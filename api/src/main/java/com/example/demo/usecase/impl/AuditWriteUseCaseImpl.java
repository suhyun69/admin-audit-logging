package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditService;
import com.example.demo.usecase.AuditWriteUseCase;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import com.example.demo.usecase.support.AuditRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AuditWriteUseCaseImpl implements AuditWriteUseCase {
    private final AuditService audit;
    private final AuditRequestMapper mapper;
    public AuditWriteUseCaseImpl(AuditService audit, AuditRequestMapper mapper) { this.audit = audit; this.mapper = mapper; }
    @Override public void record(AuditWriteAppRequest request) { audit.record(mapper.event(request), mapper.changes(request)); }
}
