package com.example.demo.audit.application;
import com.example.demo.audit.application.port.out.AuditQueryPort;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class AuditQueryService {
    private final AuditQueryPort queries;
    public AuditQueryService(AuditQueryPort queries) { this.queries=queries; }
    public List<AuditEvent> search(AuditQuery query) { return queries.search(query); }
}
