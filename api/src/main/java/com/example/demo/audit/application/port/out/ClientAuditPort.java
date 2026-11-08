package com.example.demo.audit.application.port.out;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.ClientEventIdentity;
import java.util.Optional;
import java.util.UUID;
public interface ClientAuditPort {
    Optional<ClientEventIdentity> identity(UUID id);
    boolean insertOnce(AuditEvent event);
}
