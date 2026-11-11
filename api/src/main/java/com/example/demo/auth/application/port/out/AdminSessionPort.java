package com.example.demo.auth.application.port.out;
import com.example.demo.auth.domain.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface AdminSessionPort {
    Optional<AdminAccount> findAccount(String username);
    java.util.List<AdminAccount> enabledAccounts();
    void createSession(AdminSession session);
    Optional<AuthenticatedSession> findActiveSession(String tokenHash, Instant now);
    void revoke(UUID sessionId, Instant now);
}
