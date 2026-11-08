package com.example.demo.auth.domain;
import java.time.Instant;
import java.util.UUID;
public record AdminSession(UUID id, long adminUserId, String tokenHash, Instant createdAt, Instant expiresAt, Instant revokedAt) {
    public boolean active(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    @Override public String toString() { return "AdminSession[id=" + id + "]"; }
}
