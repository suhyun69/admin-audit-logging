package com.example.demo.controller;

import com.example.demo.usecase.AuditStreamUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class AuditStreamController {
    private final AuditStreamUseCase stream;
    public AuditStreamController(AuditStreamUseCase stream) { this.stream=stream; }
    @GetMapping(value="/api/v1/audit-logs/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CookieValue(name="AUDIT_SESSION",required=false) String token,
            @RequestHeader(name="Last-Event-ID",required=false) String cursor,@RequestParam java.util.Map<String,String> params,HttpServletResponse response) {
        if(!params.isEmpty()) throw new com.example.demo.common.exception.ApiException(com.example.demo.common.exception.ErrorCode.VALIDATION_ERROR);
        response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Accel-Buffering","no");
        return stream.subscribe(token,cursor);
    }
}
