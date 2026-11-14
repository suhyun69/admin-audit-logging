package com.example.demo.usecase;

import com.example.demo.usecase.dto.ClientEventAppRequest;
import com.example.demo.usecase.dto.ClientEventAppResponse;

public interface ClientEventUseCase {
  ClientEventAppResponse accept(ClientEventAppRequest request);
}
