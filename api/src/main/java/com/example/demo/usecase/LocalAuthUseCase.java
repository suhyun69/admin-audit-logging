package com.example.demo.usecase;

import com.example.demo.usecase.dto.ActorAppResponse;
import com.example.demo.usecase.dto.LoginAppResponse;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LocalAuthUseCase {
    List<ActorAppResponse> accounts();
    LoginAppResponse login(String username);
    SseEmitter stream(String lastEventId);
}
