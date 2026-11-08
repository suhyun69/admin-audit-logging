package com.example.demo.audit.application.port.out;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditQuery;
import java.util.*;
import com.example.demo.audit.domain.AuditChange;
import com.example.demo.audit.domain.AuditPosition;
public interface AuditQueryPort {
    List<AuditEvent> search(AuditQuery query);
    Optional<AuditEvent> detail(UUID id);
    List<AuditEvent> interaction(UUID id,int limit,AuditPosition cursor);
    Map<UUID,List<AuditChange>> changes(List<UUID> ids);
}
