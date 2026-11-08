package com.example.demo.controller;

import com.example.demo.controller.dto.ClientEventWebRequest;
import com.example.demo.controller.dto.ClientEventWebResponse;
import com.example.demo.usecase.ClientEventUseCase;
import com.example.demo.usecase.dto.ClientEventAppRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-events/client")
public class ClientAuditController {
    private final ClientEventUseCase events;
    public ClientAuditController(ClientEventUseCase events) { this.events=events; }
    @PostMapping
    public ResponseEntity<ClientEventWebResponse> accept(@RequestBody ClientEventWebRequest body) {
        var result=events.accept(new ClientEventAppRequest(body.eventId(),body.interactionId(),body.eventType(),body.action(),body.resourceType(),body.resourceId(),body.pagePath(),body.occurredAt()));
        return ResponseEntity.status(result.created()?201:200).body(new ClientEventWebResponse(result.id()));
    }
}
