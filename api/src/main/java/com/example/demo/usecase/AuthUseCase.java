package com.example.demo.usecase;
import com.example.demo.usecase.dto.*;
import java.util.Optional;
import java.util.UUID;
public interface AuthUseCase {
    LoginAppResponse login(LoginAppRequest request);
    Optional<SessionActorAppResponse> authenticate(String token);
    void logout(UUID sessionId);
}
