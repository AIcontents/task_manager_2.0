package com.example.service;

import com.example.model.AuditLog;
import com.example.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Аудит-журнал. Отдельный Spring-бин: вызов this.logAction() внутри TaskServiceImpl не прошёл бы через прокси,
 * и REQUIRES_NEW не сработал бы.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * REQUIRES_NEW - своя независимая транзакция: запись остаётся в БД, даже если основная операция
     * (создание или удаление задачи) потом откатится.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, Long taskId) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setTaskId(taskId);
        entry.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(entry);
    }
}
