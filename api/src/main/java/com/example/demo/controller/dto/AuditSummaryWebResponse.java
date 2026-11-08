package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.AuditSummaryAppResponse;
import java.time.Instant;
import java.util.List;
public record AuditSummaryWebResponse(Instant from,Instant to,long totalEvents,long deniedEvents,long errorEvents,List<AuditItemWebResponse> recentEvents) {
    public static AuditSummaryWebResponse from(AuditSummaryAppResponse summary) { return new AuditSummaryWebResponse(summary.from(),summary.to(),summary.totalEvents(),summary.deniedEvents(),summary.errorEvents(),summary.recentEvents().stream().map(AuditItemWebResponse::from).toList()); }
}
