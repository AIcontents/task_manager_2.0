package com.example.service;

import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskNotFoundException;
import com.example.model.AuditLog;
import com.example.model.Priority;
import com.example.repository.AuditLogRepository;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты транзакций на настоящей H2. Сами тестовые методы НЕ помечены @Transactional:
 * иначе транзакцию теста откатывал бы Spring, и проверять commit/rollback сервисов было бы нельзя.
 * Поэтому после каждого теста таблицы чистятся вручную.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tx-behavior-test")
@ActiveProfiles("test")
@Import(TransactionDemoService.class)
class TransactionBehaviorTest {

    @Autowired
    private TransactionDemoService demoService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void checkedException_withoutRollbackFor_shouldStillCommitTask() {
        assertThatThrownBy(() -> demoService.createWithChecked("checked без rollbackFor"))
                .isInstanceOf(IOException.class);

        // Проблема: исключение было, а данные записаны
        assertThat(taskRepository.count()).isEqualTo(1);
    }

    @Test
    void create_whenCheckedExceptionAfterSave_withRollbackFor_shouldRollback() {
        assertThatThrownBy(() -> demoService.createWithCheckedAndRollbackFor("checked с rollbackFor"))
                .isInstanceOf(IOException.class);

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void create_whenExceptionAfterSave_shouldRollback() {
        assertThatThrownBy(() -> demoService.createThenFail("runtime exception"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void auditLog_savedEvenWhenMainTransactionRolledBack() {
        assertThatThrownBy(() -> demoService.createThenFail("аудит переживёт откат"))
                .isInstanceOf(IllegalStateException.class);

        // основная транзакция откатилась - задачи нет, а REQUIRES_NEW-запись аудита закоммичена
        assertThat(taskRepository.count()).isZero();
        assertThat(auditLogRepository.findAll()).hasSize(1);
        assertThat(auditLogRepository.findAll().get(0).getAction()).isEqualTo("CREATE");
    }

    @Test
    void taskService_create_shouldWriteAuditRecord() {
        TaskResponse created = taskService.create(new TaskRequest("С аудитом", "описание", Priority.LOW));

        assertThat(auditLogRepository.findAll())
                .extracting(AuditLog::getAction, AuditLog::getTaskId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CREATE", created.getId()));
    }

    @Test
    void taskService_delete_whenTaskMissing_shouldThrowAndWriteNoAudit() {
        assertThatThrownBy(() -> taskService.delete(999_999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999999");

        assertThat(auditLogRepository.count()).isZero();
    }
}
