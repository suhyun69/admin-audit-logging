package com.example.demo.usecase;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AuditStreamUseCase {
    SseEmitter subscribe(String token, String lastEventId);
}
