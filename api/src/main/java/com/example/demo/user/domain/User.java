package com.example.demo.user.domain;
import java.time.Instant;
public record User(long id,String email,String name,UserStatus status,long version,Instant createdAt,Instant updatedAt) {}
