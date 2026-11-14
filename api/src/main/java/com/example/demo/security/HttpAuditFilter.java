package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpAuditFilter extends OncePerRequestFilter {
  private final HttpAuditExecution execution;

  public HttpAuditFilter(HttpAuditExecution execution) {
    this.execution = execution;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/")
        || (request.getRequestURI().equals("/api/v1/audit-logs/stream")
            || request.getRequestURI().equals("/api/v1/local/audit-logs/stream"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    execution.execute(request, response, chain);
  }
}
