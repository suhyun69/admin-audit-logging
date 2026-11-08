package com.example.demo.audit.application;

import com.example.demo.audit.application.port.out.ClientAuditPort;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.ClientEventIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class ClientAuditService {
    private final ClientAuditPort events;
    public ClientAuditService(ClientAuditPort events) { this.events=events; }
    public Optional<ClientEventIdentity> identity(UUID id) { return events.identity(id); }
    public boolean insertOnce(AuditEvent event) { return events.insertOnce(event); }
}
