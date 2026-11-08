package com.example.demo.usecase.dto;
import com.example.demo.common.page.AuditCursorPosition;
import java.util.UUID;
public record NormalizedInteractionAppRequest(UUID id,int size,AuditCursorPosition cursor) {}
