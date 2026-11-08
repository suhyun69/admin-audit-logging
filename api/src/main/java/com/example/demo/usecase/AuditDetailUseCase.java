package com.example.demo.usecase;
import com.example.demo.usecase.dto.*;
public interface AuditDetailUseCase { AuditDetailAppResponse detail(String id);AuditDetailsPageAppResponse interaction(String id,InteractionPageAppRequest request); }
