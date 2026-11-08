package com.example.demo.audit.domain;
public record ClientEventIdentity(Long actorId,AuditSource source,String payloadDigest) {}
