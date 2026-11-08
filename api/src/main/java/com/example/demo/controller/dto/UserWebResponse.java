package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.UserAppResponse;
import java.time.Instant;
public record UserWebResponse(long id,String email,String name,String status,long version,Instant createdAt,Instant updatedAt) {
    public static UserWebResponse from(UserAppResponse user) { return new UserWebResponse(user.id(),user.email(),user.name(),user.status(),user.version(),user.createdAt(),user.updatedAt()); }
}
