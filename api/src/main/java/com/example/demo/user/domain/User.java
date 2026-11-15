package com.example.demo.user.domain;

import java.time.Instant;

public record User(
    long id,
    String email,
    String name,
    UserStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt,
    long groupId) {
  public User(
      long id,
      String email,
      String name,
      UserStatus status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this(id, email, name, status, version, createdAt, updatedAt, 1L);
  }
}
