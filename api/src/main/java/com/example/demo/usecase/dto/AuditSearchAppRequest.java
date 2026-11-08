package com.example.demo.usecase.dto;
public record AuditSearchAppRequest(String actorId,String eventType,String result,String resourceType,String resourceId,
        String interactionId,String requestId,String from,String to,String size,String cursor) {
    @Override public String toString() { return "AuditSearchAppRequest[filters redacted]"; }
}
