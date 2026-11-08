package com.example.demo.audit.domain;
import java.time.Instant;
import java.util.UUID;
public record AuditQuery(Long actorId,AuditEventType eventType,AuditResult result,String resourceType,String resourceId,
                         UUID interactionId,UUID requestId,Instant from,Instant to,int limit,AuditPosition cursor) {}
