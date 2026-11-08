package com.example.demo.common.web;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.SecurityFailureObserver;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final List<SecurityFailureObserver> securityObservers;
    public GlobalExceptionHandler() { this.securityObservers=List.of(); }
    @Autowired public GlobalExceptionHandler(List<SecurityFailureObserver> observers) { this.securityObservers=List.copyOf(observers); }
    private void observeSecurity(ErrorCode code) {
        for(var observer:securityObservers) {
            try { observer.failed(code); }
            catch(RuntimeException exception) { log.error("Security observation failed type={}",exception.getClass().getSimpleName()); }
        }
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> business(BusinessException exception) { return response(exception.getErrorCode()); }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception exception) { return response(ErrorCode.VALIDATION_ERROR); }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException exception) { observeSecurity(ErrorCode.ACCESS_DENIED); return response(ErrorCode.ACCESS_DENIED); }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated(AuthenticationException exception) { observeSecurity(ErrorCode.UNAUTHENTICATED); return response(ErrorCode.UNAUTHENTICATED); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException exception) {
        var body = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
        var builder = ResponseEntity.status(405);
        var methods = exception.getSupportedHttpMethods();
        if (methods != null) builder.allow(methods.toArray(org.springframework.http.HttpMethod[]::new));
        return builder.body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException exception) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> missing(NoResourceFoundException exception) { return response(ErrorCode.RESOURCE_NOT_FOUND); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        // Never log exception messages, SQL, request parameters, or bodies.
        log.error("Unhandled request failure type={}", exception.getClass().getSimpleName());
        return response(ErrorCode.INTERNAL_ERROR);
    }

    // RequestContextFilter owns X-Request-ID; ResponseEntity headers append in a real Servlet response.
    private ResponseEntity<ErrorResponse> response(ErrorCode code) {
        var body = ErrorResponse.of(code);
        return ResponseEntity.status(ApiErrors.status(code)).body(body);
    }
}
