package com.example.demo.auth.application;

import com.example.demo.auth.application.port.out.AdminSessionPort;
import com.example.demo.auth.domain.*;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AuthService {
    private final AdminSessionPort sessions;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;
    public AuthService(AdminSessionPort sessions, PasswordEncoder passwords, Clock clock) {
        this.sessions = sessions; this.passwords = passwords; this.clock = clock;
        this.dummyHash = passwords.encode("unmatched-credential");
    }
    public IssuedSession login(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 100 || password == null
                || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        var account = sessions.findAccount(username).orElse(null);
        boolean matches = passwords.matches(password, account == null ? dummyHash : account.passwordHash());
        if (account == null || !account.enabled() || !matches) throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = clock.instant();
        var session = new AdminSession(UUID.randomUUID(), account.id(), hash(token), now, now.plus(Duration.ofHours(8)), null);
        sessions.createSession(session);
        return new IssuedSession(account, session, token);
    }
    public Optional<AuthenticatedSession> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return sessions.findActiveSession(hash(token), clock.instant());
    }
    public void logout(UUID sessionId) { sessions.revoke(sessionId, clock.instant()); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
