package com.example.demo.common.page;
import java.time.Instant;
import java.util.UUID;
public record AuditCursorPosition(Instant occurredAt,UUID id) {}
