package com.example.demo.usecase.dto;
import java.time.Instant;
public record UserAppResponse(long id,String email,String name,String status,long version,Instant createdAt,Instant updatedAt) {}
