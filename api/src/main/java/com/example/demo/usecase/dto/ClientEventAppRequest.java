package com.example.demo.usecase.dto;
public record ClientEventAppRequest(String eventId,String interactionId,String eventType,String action,String resourceType,String resourceId,String pagePath,String occurredAt) {
    @Override public String toString() { return "ClientEventAppRequest[redacted]"; }
}
