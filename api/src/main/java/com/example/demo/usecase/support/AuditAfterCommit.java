package com.example.demo.usecase.support;

import com.example.demo.usecase.ObserveAuditUseCase;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AuditAfterCommit {
    private final ObserveAuditUseCase observer;
    public AuditAfterCommit(ObserveAuditUseCase observer) { this.observer=observer; }
    public void record(AuditWriteAppRequest event) {
        if(!TransactionSynchronizationManager.isSynchronizationActive()) { observer.record(event); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { observer.record(event); }
        });
    }
}
