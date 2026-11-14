package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditQueryService;
import com.example.demo.audit.domain.AuditQuery;
import com.example.demo.usecase.AuditSummaryTransactionUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.dto.AuditSummaryAppResponse;
import com.example.demo.usecase.dto.NormalizedAuditSearchAppRequest;
import com.example.demo.usecase.support.AuditResponseMapper;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class AuditSummaryTransactionUseCaseImpl implements AuditSummaryTransactionUseCase {
  private final AuthorizationUseCase authorization;
  private final AuditQueryService queries;
  private final AuditResponseMapper mapper;

  public AuditSummaryTransactionUseCaseImpl(
      AuthorizationUseCase authorization, AuditQueryService queries, AuditResponseMapper mapper) {
    this.authorization = authorization;
    this.queries = queries;
    this.mapper = mapper;
  }

  @Override
  @Audited(Operation.AUDIT_SUMMARY)
  public AuditSummaryAppResponse summary(NormalizedAuditSearchAppRequest request) {
    var actor = authorization.requireAuthenticated();
    Long scope = "ADMIN".equals(actor.role()) ? null : actor.id();
    if (scope == null) authorization.requireAuditAdmin();
    if (!Objects.equals(scope, request.actorId())) throw new AccessDeniedException("Access denied");
    var summary = queries.summary(scope, request.from(), request.to());
    var recent =
        queries
            .search(
                new AuditQuery(
                    scope,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    request.from(),
                    request.to(),
                    10,
                    null))
            .stream()
            .map(mapper::item)
            .toList();
    var response =
        new AuditSummaryAppResponse(
            request.from(),
            request.to(),
            summary.totalEvents(),
            summary.deniedEvents(),
            summary.errorEvents(),
            recent);
    return response;
  }
}
