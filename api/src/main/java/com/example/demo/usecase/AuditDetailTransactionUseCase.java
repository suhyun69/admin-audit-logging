package com.example.demo.usecase;
import com.example.demo.usecase.dto.*;
import java.util.UUID;
public interface AuditDetailTransactionUseCase { AuditDetailAppResponse detail(UUID id);AuditDetailsPageAppResponse interaction(NormalizedInteractionAppRequest request); }
