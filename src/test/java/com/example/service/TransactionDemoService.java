package com.example.service;

import com.example.model.Priority;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Демонстрация правил отката транзакций (Часть 5, фаза 2 и 3). Это ТЕСТОВЫЙ бин: временные методы из ТЗ
 * ("создать временный createWithChecked ... убрать из кода") живут здесь, а не в production-коде.
 * @TestComponent не попадает в component scan - подключается явно через @Import в тесте.
 */
@TestComponent
public class TransactionDemoService {

    private final TaskRepository taskRepository;
    private final AuditService auditService;

    public TransactionDemoService(TaskRepository taskRepository, AuditService auditService) {
        this.taskRepository = taskRepository;
        this.auditService = auditService;
    }

    /** Checked exception БЕЗ rollbackFor: транзакция ЗАКОММИТИТСЯ, задача останется в БД. */
    @Transactional
    public Task createWithChecked(String title) throws IOException {
        taskRepository.save(newTask(title));
        throw new IOException("что-то пошло не так");
    }

    /** С rollbackFor = Exception.class откатывается и checked exception: задачи в БД не будет. */
    @Transactional(rollbackFor = Exception.class)
    public Task createWithCheckedAndRollbackFor(String title) throws IOException {
        taskRepository.save(newTask(title));
        throw new IOException("что-то пошло не так");
    }

    /**
     * RuntimeException откатывает основную транзакцию по умолчанию, а запись аудита (REQUIRES_NEW)
     * коммитится в собственной транзакции и остаётся.
     */
    @Transactional
    public Task createThenFail(String title) {
        Task saved = taskRepository.save(newTask(title));
        auditService.logAction("CREATE", saved.getId());
        throw new IllegalStateException("сбой после сохранения");
    }

    private static Task newTask(String title) {
        return new Task(title, "demo", Priority.MEDIUM);
    }
}
