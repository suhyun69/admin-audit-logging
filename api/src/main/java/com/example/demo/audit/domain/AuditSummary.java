package com.example.demo.audit.domain;
public record AuditSummary(long totalEvents,long deniedEvents,long errorEvents) {}
