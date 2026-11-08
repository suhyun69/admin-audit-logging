package com.example.demo.controller;

import com.example.demo.controller.dto.AuditPageWebResponse;
import com.example.demo.usecase.AuditSearchUseCase;
import com.example.demo.usecase.dto.AuditSearchAppRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {
    private final AuditSearchUseCase searches;
    public AuditLogController(AuditSearchUseCase searches) { this.searches=searches; }
    @GetMapping
    public AuditPageWebResponse search(@RequestParam Map<String,String> params) { return AuditPageWebResponse.from(searches.search(request(params))); }
    @GetMapping("/me")
    public AuditPageWebResponse mine(@RequestParam Map<String,String> params) { return AuditPageWebResponse.from(searches.mine(request(params))); }
    private AuditSearchAppRequest request(Map<String,String> params) {
        return new AuditSearchAppRequest(params.get("actorId"),params.get("eventType"),params.get("result"),params.get("resourceType"),params.get("resourceId"),params.get("interactionId"),params.get("requestId"),params.get("from"),params.get("to"),params.get("size"),params.get("cursor"));
    }
}
