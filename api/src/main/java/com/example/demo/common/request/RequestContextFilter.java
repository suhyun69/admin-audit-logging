package com.example.demo.common.request;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.web.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component("auditRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestContextFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);
    private final ObjectMapper mapper;
    public RequestContextFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var previousMdc = MDC.getCopyOfContextMap();
        UUID requestId = UUID.randomUUID();
        RequestContext.install(new RequestContext(requestId, null, Instant.now()));
        request.setAttribute(RequestContext.ATTRIBUTE, RequestContext.current().orElseThrow());
        MDC.put("requestId", requestId.toString());
        response.setHeader("X-Request-ID", requestId.toString());
        try {
            String header = request.getHeader("X-Interaction-ID");
            UUID interactionId = header == null ? null : AuditPrivacy.uuid(header);
            var context = new RequestContext(requestId, interactionId, RequestContext.current().orElseThrow().startedAt());
            RequestContext.install(context);
            request.setAttribute(RequestContext.ATTRIBUTE, context);
            if (interactionId != null) MDC.put("interactionId", interactionId.toString());
            chain.doFilter(request, response);
        } catch (ApiException exception) {
            if (response.isCommitted()) throw exception;
            response.resetBuffer();
            ApiErrors.write(response, mapper, exception.getErrorCode());
        } catch (Exception exception) {
            if (response.isCommitted()) {
                if (exception instanceof IOException io) throw io;
                if (exception instanceof ServletException servlet) throw servlet;
                throw new ServletException(exception);
            }
            log.error("Request filter failure type={}", exception.getClass().getSimpleName());
            response.resetBuffer();
            ApiErrors.write(response, mapper, ErrorCode.INTERNAL_ERROR);
        } finally {
            RequestContext.clear();
            MDC.clear();
            if (previousMdc != null) MDC.setContextMap(previousMdc);
        }
    }
}
