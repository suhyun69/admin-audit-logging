package com.example.demo.usecase;

import com.example.demo.usecase.dto.LoginAppRequest;
import com.example.demo.usecase.dto.LoginAppResponse;
import com.example.demo.usecase.dto.SessionActorAppResponse;
import java.util.Optional;
import java.util.UUID;

public interface AuthUseCase {
  LoginAppResponse login(LoginAppRequest request);

  Optional<SessionActorAppResponse> authenticate(String token);

  void logout(UUID sessionId);
}
