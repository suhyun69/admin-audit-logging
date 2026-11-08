package com.example.demo.common.web;

import com.example.demo.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class ApiErrors {
    private ApiErrors() {}
    public static int status(ErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR, INVALID_CURSOR -> 400;
            case UNAUTHENTICATED, INVALID_CREDENTIALS -> 401;
            case ACCESS_DENIED, CSRF_INVALID -> 403;
            case USER_NOT_FOUND, AUDIT_NOT_FOUND, RESOURCE_NOT_FOUND -> 404;
            case VERSION_CONFLICT, EVENT_CONFLICT -> 409;
            case PAYLOAD_TOO_LARGE -> 413;
            case RATE_LIMITED -> 429;
            case INTERNAL_ERROR -> 500;
        };
    }
    public static void write(HttpServletResponse response, ObjectMapper mapper, ErrorCode code) throws IOException {
        ErrorResponse body = ErrorResponse.of(code);
        response.setStatus(status(code));
        response.setHeader("X-Request-ID", body.requestId().toString());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
