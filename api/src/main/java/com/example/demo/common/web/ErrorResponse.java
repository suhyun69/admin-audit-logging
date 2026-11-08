package com.example.demo.common.web;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.RequestContext;
import java.util.UUID;

public record ErrorResponse(String code, String message, UUID requestId) {
    public static ErrorResponse of(ErrorCode code) {
        UUID requestId = RequestContext.current().map(RequestContext::requestId).orElseGet(UUID::randomUUID);
        return new ErrorResponse(code.name(), code.getMessage(), requestId);
    }
}
