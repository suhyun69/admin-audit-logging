package com.example.demo.usecase.dto;
import java.time.Instant;
import java.util.List;
public record AuditSummaryAppResponse(Instant from,Instant to,long totalEvents,long deniedEvents,long errorEvents,List<AuditItemAppResponse> recentEvents) {}
