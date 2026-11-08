package com.example.demo.usecase;

import com.example.demo.common.request.ActorSnapshot;
import org.springframework.security.access.prepost.PreAuthorize;

public interface AuthorizationUseCase {
    @PreAuthorize("isAuthenticated()")
    ActorSnapshot requireAuthenticated();
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    ActorSnapshot requireUserWrite();
    @PreAuthorize("hasRole('ADMIN')")
    ActorSnapshot requireAuditAdmin();
    @PreAuthorize("hasRole('ADMIN')")
    ActorSnapshot requireAdminSettings();
}
