package com.example.demo.controller;

import com.example.demo.controller.dto.AuditPageWebResponse;
import com.example.demo.controller.dto.AuditDetailWebResponse;
import com.example.demo.controller.dto.AuditDetailsPageWebResponse;
import com.example.demo.usecase.AuditDetailUseCase;
import com.example.demo.usecase.dto.InteractionPageAppRequest;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.AuditSearchUseCase;
import com.example.demo.usecase.dto.AuditSearchAppRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {
    private final AuditSearchUseCase searches;
    private final AuditDetailUseCase details;
    public AuditLogController(AuditSearchUseCase searches,AuditDetailUseCase details) { this.searches=searches;this.details=details; }
    @GetMapping
    public AuditPageWebResponse search(@RequestParam Map<String,String> params) { return AuditPageWebResponse.from(searches.search(request(params))); }
    @GetMapping("/me")
    public AuditPageWebResponse mine(@RequestParam Map<String,String> params) { return AuditPageWebResponse.from(searches.mine(request(params))); }
    @GetMapping("/{id}")
    public AuditDetailWebResponse detail(@PathVariable String id) { return AuditDetailWebResponse.from(details.detail(id)); }
    @GetMapping("/interactions/{id}")
    public AuditDetailsPageWebResponse interaction(@PathVariable String id,@RequestParam Map<String,String> params) {
        if(!java.util.Set.of("size","cursor").containsAll(params.keySet()))throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return AuditDetailsPageWebResponse.from(details.interaction(id,new InteractionPageAppRequest(params.get("size"),params.get("cursor"))));
    }
    private AuditSearchAppRequest request(Map<String,String> params) {
        return new AuditSearchAppRequest(params.get("actorId"),params.get("eventType"),params.get("result"),params.get("resourceType"),params.get("resourceId"),params.get("interactionId"),params.get("requestId"),params.get("from"),params.get("to"),params.get("size"),params.get("cursor"));
    }
}
