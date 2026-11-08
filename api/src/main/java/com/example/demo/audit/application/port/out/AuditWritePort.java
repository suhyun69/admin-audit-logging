package com.example.demo.audit.application.port.out;

import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditChange;
import java.util.List;

public interface AuditWritePort {
    void insert(AuditEvent event, List<AuditChange> changes);
}
