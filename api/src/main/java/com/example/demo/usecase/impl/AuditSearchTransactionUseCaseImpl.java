package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditQueryService;
import com.example.demo.audit.domain.AuditEventType;
import com.example.demo.audit.domain.AuditPosition;
import com.example.demo.audit.domain.AuditQuery;
import com.example.demo.audit.domain.AuditResult;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.AuditSearchTransactionUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.dto.AuditPageAppResponse;
import com.example.demo.usecase.dto.NormalizedAuditSearchAppRequest;
import com.example.demo.usecase.support.AuditResponseMapper;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditSearchTransactionUseCaseImpl implements AuditSearchTransactionUseCase {
  private final AuditQueryService queries;
  private final AuthorizationUseCase authorization;
  private final CursorCodec cursors;
  private final AuditResponseMapper mapper;

  public AuditSearchTransactionUseCaseImpl(
      AuditQueryService queries,
      AuthorizationUseCase authorization,
      CursorCodec cursors,
      AuditResponseMapper mapper) {
    this.queries = queries;
    this.authorization = authorization;
    this.cursors = cursors;
    this.mapper = mapper;
  }

  @Override
  @Audited(Operation.AUDIT_SEARCH)
  public AuditPageAppResponse search(NormalizedAuditSearchAppRequest request) {
    if (request.action().equals("AUDIT_ME")) {
      var actor = authorization.requireAuthenticated();
      if (!Long.valueOf(actor.id()).equals(request.actorId()))
        throw new org.springframework.security.access.AccessDeniedException("Access denied");
    } else authorization.requireAuditAdmin();
    var cursor =
        request.cursor() == null
            ? null
            : new AuditPosition(request.cursor().occurredAt(), request.cursor().id());
    var found =
        queries.search(
            new AuditQuery(
                request.actorId(),
                request.eventType() == null ? null : AuditEventType.valueOf(request.eventType()),
                request.result() == null ? null : AuditResult.valueOf(request.result()),
                request.resourceType(),
                request.resourceId(),
                request.interactionId(),
                request.requestId(),
                request.from(),
                request.to(),
                request.size() + 1,
                cursor));
    boolean hasNext = found.size() > request.size();
    var page = found.stream().limit(request.size()).map(mapper::item).toList();
    String next =
        hasNext
            ? cursors.audit(page.get(page.size() - 1).occurredAt(), page.get(page.size() - 1).id())
            : null;
    // Read the snapshot before inserting the audit of this query.
    return new AuditPageAppResponse(page, next, hasNext);
  }
}
