package com.example.demo.usecase.impl;

import com.example.demo.audit.application.AuditQueryService;
import com.example.demo.audit.domain.AuditPosition;
import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.AuditDetailTransactionUseCase;
import com.example.demo.usecase.AuthorizationUseCase;
import com.example.demo.usecase.dto.AuditDetailAppResponse;
import com.example.demo.usecase.dto.AuditDetailsPageAppResponse;
import com.example.demo.usecase.dto.NormalizedInteractionAppRequest;
import com.example.demo.usecase.support.AuditResponseMapper;
import com.example.demo.usecase.support.audit.Audited;
import com.example.demo.usecase.support.audit.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class AuditDetailTransactionUseCaseImpl implements AuditDetailTransactionUseCase {
  private final AuthorizationUseCase authorization;
  private final AuditQueryService queries;
  private final AuditResponseMapper mapper;
  private final CursorCodec cursors;

  public AuditDetailTransactionUseCaseImpl(
      AuthorizationUseCase authorization,
      AuditQueryService queries,
      AuditResponseMapper mapper,
      CursorCodec cursors) {
    this.authorization = authorization;
    this.queries = queries;
    this.mapper = mapper;
    this.cursors = cursors;
  }

  @Override
  @Audited(Operation.AUDIT_DETAIL)
  public AuditDetailAppResponse detail(UUID id) {
    authorization.requireAuditAdmin();
    var event = queries.detail(id);
    var changes = queries.changes(List.of(id));
    var response = mapper.detail(event, changes.getOrDefault(id, List.of()));
    return response;
  }

  @Override
  @Audited(Operation.AUDIT_TIMELINE)
  public AuditDetailsPageAppResponse interaction(NormalizedInteractionAppRequest request) {
    authorization.requireAuditAdmin();
    var cursor =
        request.cursor() == null
            ? null
            : new AuditPosition(request.cursor().occurredAt(), request.cursor().id());
    var found = queries.interaction(request.id(), request.size() + 1, cursor);
    boolean hasNext = found.size() > request.size();
    var page = found.stream().limit(request.size()).toList();
    var changes = queries.changes(page.stream().map(event -> event.id()).toList());
    var items =
        page.stream()
            .map(event -> mapper.detail(event, changes.getOrDefault(event.id(), List.of())))
            .toList();
    String next =
        hasNext
            ? cursors.audit(page.get(page.size() - 1).occurredAt(), page.get(page.size() - 1).id())
            : null;
    return new AuditDetailsPageAppResponse(items, next, hasNext);
  }
}
