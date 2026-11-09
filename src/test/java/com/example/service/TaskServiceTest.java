package com.example.service;

import com.example.config.AppProperties;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskLimitExceededException;
import com.example.exception.TaskNotFoundException;
import com.example.model.Priority;
import com.example.model.Status;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты TaskService БЕЗ Spring-контекста (нет @SpringBootTest): зависимости заменены моками Mockito,
 * тесты выполняются за доли секунды. Структура каждого теста - AAA (Arrange, Act, Assert).
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ObjectProvider<TaskStatsService> statsProvider;

    // Настоящий объект (лимит 100, приоритет по умолчанию MEDIUM); @Spy тоже участвует в @InjectMocks
    @Spy
    private AppProperties appProperties = new AppProperties("Test", 100, "MEDIUM");

    // Constructor injection: Mockito передаёт моки и spy в конструктор TaskServiceImpl
    @InjectMocks
    private TaskServiceImpl taskService;

    // ---------- getAll() ----------

    @Test
    void getAll_shouldReturnAllTasksAsDtos() {
        // Arrange
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW);
        when(taskRepository.findAll()).thenReturn(List.of(task));

        // Act
        List<TaskResponse> result = taskService.getAll();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Buy milk");
        assertThat(result.get(0).getPriority()).isEqualTo("HIGH");
        assertThat(result.get(0).getStatus()).isEqualTo("NEW");
    }

    @Test
    void getAll_whenEmpty_shouldReturnEmptyList() {
        when(taskRepository.findAll()).thenReturn(List.of());

        List<TaskResponse> result = taskService.getAll();

        assertThat(result).isEmpty();
    }

    @Test
    void getAll_withStatusAndPriorityFilters_shouldUseCombinedDerivedQuery() {
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW);
        when(taskRepository.findByStatusAndPriority(eq(Status.NEW), eq(Priority.HIGH), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        // регистр значений не важен
        Page<TaskResponse> result = taskService.getAll("new", "High", 0, 10, "id");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Buy milk");
        verify(taskRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAll_withoutFilters_shouldUseFindAllWithRequestedPageAndSort() {
        when(taskRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        taskService.getAll(null, null, 2, 5, "title,desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getAll_withUnknownStatus_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> taskService.getAll("BOGUS", null, 0, 10, "id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS")
                .hasMessageContaining("IN_PROGRESS");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void getAll_withInvalidSizeOrSortField_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> taskService.getAll(null, null, 0, 0, "id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> taskService.getAll(null, null, 0, 10, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    // ---------- create() ----------

    @Test
    void create_shouldSaveTaskWithStatusNew() {
        TaskRequest request = buildRequest("Buy milk", Priority.HIGH);
        Task savedTask = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW);
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskResponse response = taskService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("NEW");
        assertThat(response.getPriority()).isEqualTo("HIGH");
    }

    @Test
    void create_shouldCallRepositorySave() {
        Task savedTask = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW);
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        taskService.create(buildRequest("Buy milk", Priority.HIGH));

        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void create_shouldWriteCreateAuditRecord() {
        when(taskRepository.save(any(Task.class))).thenReturn(buildTask(7L, "Buy milk", Priority.LOW, Status.NEW));

        taskService.create(buildRequest("Buy milk", Priority.LOW));

        verify(auditService).logAction("CREATE", 7L);
    }

    @Test
    void create_whenPriorityNotSpecified_shouldUseDefaultPriorityFromProperties() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(buildRequest("Without priority", null));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.NEW);
        assertThat(response.getPriority()).isEqualTo("MEDIUM");
    }

    @Test
    void create_whenTitleBlank_shouldThrowAndNotTouchRepository() {
        assertThatThrownBy(() -> taskService.create(buildRequest("   ", Priority.LOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");

        verifyNoInteractions(taskRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void create_whenLimitExceeded_shouldThrowTaskLimitExceededException() {
        when(taskRepository.count()).thenReturn(100L);

        assertThatThrownBy(() -> taskService.create(buildRequest("One more", Priority.LOW)))
                .isInstanceOf(TaskLimitExceededException.class)
                .hasMessageContaining("100");

        verify(taskRepository, never()).save(any(Task.class));
    }

    // ---------- findById() / getOrThrow() ----------

    @Test
    void findById_whenExists_shouldReturnTask() {
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        Optional<TaskResponse> result = taskService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Buy milk");
    }

    @Test
    void findById_whenNotExists_shouldReturnEmpty() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<TaskResponse> result = taskService.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenNotExists_shouldThrowNotFoundException() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getOrThrow(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getOrThrow_whenExists_shouldReturnTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW)));

        TaskResponse result = taskService.getOrThrow(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    // ---------- update() ----------

    @Test
    void update_whenExists_shouldReturnUpdatedTask() {
        Task existing = buildTask(1L, "Old title", Priority.LOW, Status.NEW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskRequest request = buildRequest("New title", Priority.HIGH);
        request.setDescription("New description");
        request.setStatus(Status.IN_PROGRESS);

        Optional<TaskResponse> result = taskService.update(1L, request);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("New title");
        assertThat(result.get().getDescription()).isEqualTo("New description");
        assertThat(result.get().getPriority()).isEqualTo("HIGH");
        assertThat(result.get().getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void update_whenStatusNotSpecified_shouldKeepCurrentStatus() {
        Task existing = buildTask(1L, "Old title", Priority.LOW, Status.DONE);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<TaskResponse> result = taskService.update(1L, buildRequest("New title", Priority.HIGH));

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("DONE");
    }

    @Test
    void update_whenNotExists_shouldReturnEmpty() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<TaskResponse> result = taskService.update(999L, buildRequest("Title", Priority.LOW));

        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void update_whenTitleBlank_shouldThrowIllegalArgumentException() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(buildTask(1L, "Old", Priority.LOW, Status.NEW)));

        assertThatThrownBy(() -> taskService.update(1L, buildRequest(" ", Priority.LOW)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(taskRepository, never()).save(any(Task.class));
    }

    // ---------- updateStatus() ----------

    @Test
    void updateStatus_whenExists_shouldUpdateStatusOnly() {
        Task existing = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<TaskResponse> result = taskService.updateStatus(1L, Status.DONE);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("DONE");
        assertThat(result.get().getTitle()).isEqualTo("Buy milk");
        assertThat(result.get().getPriority()).isEqualTo("HIGH");
    }

    @Test
    void updateStatus_whenNotExists_shouldReturnEmpty() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<TaskResponse> result = taskService.updateStatus(999L, Status.DONE);

        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any(Task.class));
    }

    // ---------- delete() ----------

    @Test
    void delete_shouldCallDeleteById() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        taskService.delete(1L);

        verify(taskRepository).deleteById(1L);
        verify(auditService).logAction("DELETE", 1L);
    }

    @Test
    void delete_whenNotExists_shouldThrowNotFoundAndDeleteNothing() {
        when(taskRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");

        verify(taskRepository, never()).deleteById(any());
        verifyNoInteractions(auditService);
    }

    // ---------- статистика, поиск, prototype ----------

    @Test
    void countByStatus_shouldContainAllStatusesIncludingZeroes() {
        List<Object[]> rows = List.of(new Object[]{Status.NEW, 3L}, new Object[]{Status.DONE, 2L});
        when(taskRepository.countGroupedByStatus()).thenReturn(rows);

        Map<String, Long> result = taskService.countByStatus();

        assertThat(result).containsExactly(
                entry("NEW", 3L),
                entry("IN_PROGRESS", 0L),
                entry("DONE", 2L));
    }

    @Test
    void search_shouldTrimKeywordAndMapResultsToDtos() {
        when(taskRepository.searchByKeyword("milk"))
                .thenReturn(List.of(buildTask(1L, "Buy milk", Priority.LOW, Status.NEW)));

        List<TaskResponse> result = taskService.search("  milk ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Buy milk");
    }

    @Test
    void search_whenKeywordBlank_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> taskService.search("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void getCreatedToday_shouldQueryOneDayRange() {
        when(taskRepository.findCreatedBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        taskService.getCreatedToday();

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskRepository).findCreatedBetween(from.capture(), to.capture());
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(1));
        assertThat(from.getValue().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
    }

    @Test
    void getStats_shouldReturnNewPrototypeInstanceOnEveryCall() {
        when(statsProvider.getObject()).thenReturn(new TaskStatsService(), new TaskStatsService());

        TaskStatsService first = taskService.getStats();
        TaskStatsService second = taskService.getStats();

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    // ---------- вспомогательные методы (без дублирования создания объектов) ----------

    private static Task buildTask(Long id, String title, Priority priority, Status status) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setPriority(priority);
        task.setStatus(status);
        return task;
    }

    private static TaskRequest buildRequest(String title, Priority priority) {
        TaskRequest request = new TaskRequest();
        request.setTitle(title);
        request.setPriority(priority);
        return request;
    }
}
