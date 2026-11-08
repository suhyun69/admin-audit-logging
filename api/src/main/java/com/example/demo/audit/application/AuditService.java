package com.example.demo.audit.application;

import com.example.demo.audit.application.port.out.AuditWritePort;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AuditService {
    private final AuditWritePort writer;
    public AuditService(AuditWritePort writer) { this.writer = writer; }
    public void record(AuditEvent event, List<AuditChange> changes) { writer.insert(event, changes); }
}
