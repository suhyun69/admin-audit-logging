package com.example.demo.usecase.dto;
import com.example.demo.common.page.AuditCursorPosition;
import java.time.Instant;
import java.util.UUID;
public record NormalizedAuditSearchAppRequest(Long actorId,String eventType,String result,String resourceType,String resourceId,
        UUID interactionId,UUID requestId,Instant from,Instant to,int size,AuditCursorPosition cursor,String action) {}
