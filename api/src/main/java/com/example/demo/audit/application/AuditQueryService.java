package com.example.demo.audit.application;
import com.example.demo.audit.application.port.out.AuditQueryPort;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.example.demo.audit.domain.AuditChange;
import com.example.demo.audit.domain.AuditPosition;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class AuditQueryService {
    private final AuditQueryPort queries;
    public AuditQueryService(AuditQueryPort queries) { this.queries=queries; }
    public List<AuditEvent> search(AuditQuery query) { return queries.search(query); }
    public AuditEvent detail(UUID id) { return queries.detail(id).orElseThrow(()->new ApiException(ErrorCode.AUDIT_NOT_FOUND)); }
    public List<AuditEvent> interaction(UUID id,int limit,AuditPosition cursor) { return queries.interaction(id,limit,cursor); }
    public Map<UUID,List<AuditChange>> changes(List<UUID> ids) { return queries.changes(ids); }
}
