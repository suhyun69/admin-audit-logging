package com.example.demo.audit.domain;
import java.time.Instant;
import java.util.UUID;
public record AuditPosition(Instant occurredAt,UUID id) {}
