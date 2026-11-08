package com.example.demo.controller.dto;
import com.example.demo.controller.ClientEventDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=ClientEventDeserializer.class)
public record ClientEventWebRequest(String eventId,String interactionId,String eventType,String action,String resourceType,String resourceId,String pagePath,String occurredAt) {
    @Override public String toString() { return "ClientEventWebRequest[redacted]"; }
}
