package com.example.demo.audit.domain;

public record AuditChange(String fieldName, String beforeValue, String afterValue) {
    public AuditChange {
        if (!"status".equals(fieldName) || !valid(beforeValue) || !valid(afterValue))
            throw new IllegalArgumentException("Only status changes can be audited");
    }
    private static boolean valid(String value) { return "ACTIVE".equals(value) || "SUSPENDED".equals(value); }
}
