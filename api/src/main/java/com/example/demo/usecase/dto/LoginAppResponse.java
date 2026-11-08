package com.example.demo.usecase.dto;
import java.time.Instant;
import java.util.UUID;
public record LoginAppResponse(ActorAppResponse actor, UUID sessionId, String token, Instant expiresAt) {
    @Override public String toString() { return "LoginAppResponse[session=" + sessionId + "]"; }
}
