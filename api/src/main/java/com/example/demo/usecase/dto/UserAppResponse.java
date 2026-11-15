package com.example.demo.usecase.dto;

import java.time.Instant;

public record UserAppResponse(
    long id,
    String email,
    String name,
    String status,
    long version,
    Instant createdAt,
    Instant updatedAt,
    long groupId,
    java.util.Set<String> allowedActions) {
  public UserAppResponse(
      long id,
      String email,
      String name,
      String status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this(id, email, name, status, version, createdAt, updatedAt, 1L, java.util.Set.of());
  }
}
