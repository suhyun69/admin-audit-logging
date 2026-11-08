package com.example.demo.audit.application.port.out;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditQuery;
import java.util.List;
public interface AuditQueryPort { List<AuditEvent> search(AuditQuery query); }
