package com.example.demo.usecase.dto;
public record AuditHttpAppRequest(String method, String path, Integer statusCode, Long durationMs,
                                  String ipAddress, String userAgent, String errorCode) {}
