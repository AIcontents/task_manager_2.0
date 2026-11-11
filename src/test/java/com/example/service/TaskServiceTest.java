package com.example.service;

import com.example.config.AppProperties;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskLimitExceededException;
import com.example.exception.TaskNotFoundException;
import com.example.model.Priority;
import com.example.model.Role;
import com.example.model.Status;
import com.example.model.Task;
import com.example.model.User;
import com.example.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * Текущий пользователь передаётся в сервис параметром - достаточно создать объект User, SecurityContext не нужен.
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

    private User owner;
    private User stranger;
    private User admin;

    @BeforeEach
    void setUp() {
        owner = buildUser(1L, "owner@test.com", Role.ROLE_USER);
        stranger = buildUser(2L, "stranger@test.com", Role.ROLE_USER);
        admin = buildUser(3L, "admin@test.com", Role.ROLE_ADMIN);
    }

    // ---------- getAll() ----------

    @Test
    void getAll_asAdmin_shouldReturnAllTasksAsDtos() {
        // Arrange
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner);
        when(taskRepository.findAll()).thenReturn(List.of(task));

        // Act
        List<TaskResponse> result = taskService.getAll(admin);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Buy milk");
        assertThat(result.get(0).getPriority()).isEqualTo("HIGH");
        assertThat(result.get(0).getStatus()).isEqualTo("NEW");
        assertThat(result.get(0).getOwnerEmail()).isEqualTo("owner@test.com");
    }

    @Test
    void getAll_asUser_shouldReturnOnlyOwnTasks() {
        Task own = buildTask(1L, "Own", Priority.LOW, Status.NEW, owner);
        when(taskRepository.findAllByOwner(owner)).thenReturn(List.of(own));

        List<TaskResponse> result = taskService.getAll(owner);

        assertThat(result).extracting(TaskResponse::getTitle).containsExactly("Own");
        verify(taskRepository, never()).findAll();
    }

    @Test
    void getAll_whenEmpty_shouldReturnEmptyList() {
        when(taskRepository.findAll()).thenReturn(List.of());

        List<TaskResponse> result = taskService.getAll(admin);

        assertThat(result).isEmpty();
    }

    @Test
    void getAll_withFiltersAsUser_shouldUseOwnerScopedDerivedQuery() {
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner);
        when(taskRepository.findByOwnerAndStatusAndPriority(
                eq(owner), eq(Status.NEW), eq(Priority.HIGH), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        // регистр значений не важен
        Page<TaskResponse> result = taskService.getAll("new", "High", 0, 10, "id", owner);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Buy milk");
        verify(taskRepository, never()).findByStatusAndPriority(any(), any(), any(Pageable.class));
    }

    @Test
    void getAll_withFiltersAsAdmin_shouldUseGlobalDerivedQuery() {
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner);
        when(taskRepository.findByStatusAndPriority(eq(Status.NEW), eq(Priority.HIGH), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getAll("NEW", "HIGH", 0, 10, "id", admin);

        assertThat(result.getContent()).hasSize(1);
        verify(taskRepository, never()).findByOwnerAndStatusAndPriority(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void getAll_withoutFiltersAsUser_shouldUseFindByOwnerWithRequestedPageAndSort() {
        when(taskRepository.findByOwner(eq(owner), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        taskService.getAll(null, null, 2, 5, "title,desc", owner);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findByOwner(eq(owner), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getAll_withoutFiltersAsAdmin_shouldUseFindAllWithPageable() {
        when(taskRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        taskService.getAll(null, null, 0, 10, "id", admin);

        verify(taskRepository).findAll(any(Pageable.class));
    }

    @Test
    void getAll_withoutUser_shouldReturnEmptyPageAndNotTouchRepository() {
        Page<TaskResponse> result = taskService.getAll(null, null, 0, 10, "id", null);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(taskRepository);
    }

    @Test
    void getAll_withUnknownStatus_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> taskService.getAll("BOGUS", null, 0, 10, "id", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS")
                .hasMessageContaining("IN_PROGRESS");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void getAll_withInvalidSizeOrSortField_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> taskService.getAll(null, null, 0, 0, "id", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> taskService.getAll(null, null, 0, 10, "password", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void getMyTasks_shouldReturnOnlyOwnTasksEvenForAdmin() {
        Task adminTask = buildTask(5L, "Admin task", Priority.LOW, Status.NEW, admin);
        when(taskRepository.findAllByOwner(admin)).thenReturn(List.of(adminTask));

        List<TaskResponse> result = taskService.getMyTasks(admin);

        assertThat(result).extracting(TaskResponse::getTitle).containsExactly("Admin task");
        verify(taskRepository, never()).findAll();
    }

    // ---------- create() ----------

    @Test
    void create_shouldSaveTaskWithStatusNewAndCurrentUserAsOwner() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(1L);
            return task;
        });

        TaskResponse response = taskService.create(buildRequest("Buy milk", Priority.HIGH), owner);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(owner); // как убедиться, что owner установлен правильно
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("NEW");
        assertThat(response.getPriority()).isEqualTo("HIGH");
        assertThat(response.getOwnerEmail()).isEqualTo("owner@test.com");
    }

    @Test
    void create_shouldCallRepositorySave() {
        Task savedTask = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner);
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        taskService.create(buildRequest("Buy milk", Priority.HIGH), owner);

        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void create_shouldWriteCreateAuditRecord() {
        when(taskRepository.save(any(Task.class)))
                .thenReturn(buildTask(7L, "Buy milk", Priority.LOW, Status.NEW, owner));

        taskService.create(buildRequest("Buy milk", Priority.LOW), owner);

        verify(auditService).logAction("CREATE", 7L);
    }

    @Test
    void create_whenPriorityNotSpecified_shouldUseDefaultPriorityFromProperties() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(buildRequest("Without priority", null), owner);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.NEW);
        assertThat(response.getPriority()).isEqualTo("MEDIUM");
    }

    @Test
    void create_whenTitleBlank_shouldThrowAndNotTouchRepository() {
        assertThatThrownBy(() -> taskService.create(buildRequest("   ", Priority.LOW), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");

        verifyNoInteractions(taskRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void create_whenLimitExceeded_shouldThrowTaskLimitExceededException() {
        when(taskRepository.count()).thenReturn(100L);

        assertThatThrownBy(() -> taskService.create(buildRequest("One more", Priority.LOW), owner))
                .isInstanceOf(TaskLimitExceededException.class)
                .hasMessageContaining("100");

        verify(taskRepository, never()).save(any(Task.class));
    }

    // ---------- findById() / getOrThrow() ----------

    @Test
    void findById_whenExists_shouldReturnTask() {
        Task task = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        Optional<TaskResponse> result = taskService.findById(1L, owner);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Buy milk");
    }

    @Test
    void findById_whenNotExists_shouldReturnEmpty() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<TaskResponse> result = taskService.findById(999L, owner);

        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenNotExists_shouldThrowNotFoundException() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getOrThrow(999L, owner))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getOrThrow_whenExists_shouldReturnTask() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner)));

        TaskResponse result = taskService.getOrThrow(1L, owner);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void findById_whenTaskBelongsToSomeoneElse_shouldThrowAccessDenied() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Private", Priority.HIGH, Status.NEW, owner)));

        assertThatThrownBy(() -> taskService.findById(1L, stranger))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not your task");
    }

    @Test
    void findById_asAdmin_shouldReturnForeignTask() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Private", Priority.HIGH, Status.NEW, owner)));

        Optional<TaskResponse> result = taskService.findById(1L, admin);

        assertThat(result).isPresent();
    }

    // ---------- update() ----------

    @Test
    void update_whenExists_shouldReturnUpdatedTask() {
        Task existing = buildTask(1L, "Old title", Priority.LOW, Status.NEW, owner);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskRequest request = buildRequest("New title", Priority.HIGH);
        request.setDescription("New description");
        request.setStatus(Status.IN_PROGRESS);

        Optional<TaskResponse> result = taskService.update(1L, request, owner);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("New title");
        assertThat(result.get().getDescription()).isEqualTo("New description");
        assertThat(result.get().getPriority()).isEqualTo("HIGH");
        assertThat(result.get().getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void update_whenStatusNotSpecified_shouldKeepCurrentStatus() {
        Task existing = buildTask(1L, "Old title", Priority.LOW, Status.DONE, owner);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<TaskResponse> result = taskService.update(1L, buildRequest("New title", Priority.HIGH), owner);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("DONE");
    }

    @Test
    void update_whenNotExists_shouldReturnEmpty() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<TaskResponse> result = taskService.update(999L, buildRequest("Title", Priority.LOW), owner);

        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void update_whenTitleBlank_shouldThrowIllegalArgumentException() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Old", Priority.LOW, Status.NEW, owner)));

        assertThatThrownBy(() -> taskService.update(1L, buildRequest(" ", Priority.LOW), owner))
                .isInstanceOf(IllegalArgumentException.class);

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void update_whenNotOwner_shouldThrowAccessDeniedAndNotSave() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Old", Priority.LOW, Status.NEW, owner)));

        assertThatThrownBy(() -> taskService.update(1L, buildRequest("Hacked", Priority.LOW), stranger))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, never()).save(any(Task.class));
    }

    // ---------- updateStatus() ----------

    @Test
    void updateStatus_whenExists_shouldUpdateStatusOnly() {
        Task existing = buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<TaskResponse> result = taskService.updateStatus(1L, Status.DONE, owner);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("DONE");
        assertThat(result.get().getTitle()).isEqualTo("Buy milk");
        assertThat(result.get().getPriority()).isEqualTo("HIGH");
    }

    @Test
    void updateStatus_whenNotExists_shouldReturnEmpty() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<TaskResponse> result = taskService.updateStatus(999L, Status.DONE, owner);

        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void updateStatus_whenNotOwner_shouldThrowAccessDenied() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Buy milk", Priority.HIGH, Status.NEW, owner)));

        assertThatThrownBy(() -> taskService.updateStatus(1L, Status.DONE, stranger))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, never()).save(any(Task.class));
    }

    // ---------- delete(): пользователь удаляет только свои задачи, ADMIN - любые ----------

    @Test
    void delete_shouldCallDeleteByIdAndWriteAuditRecord() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Buy milk", Priority.LOW, Status.NEW, owner)));

        taskService.delete(1L, owner);

        verify(taskRepository).deleteById(1L);
        verify(auditService).logAction("DELETE", 1L);
    }

    @Test
    void delete_whenUserIsNotOwner_shouldThrowAccessDenied() {
        // user1 создал задачу, user2 пытается её удалить
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Buy milk", Priority.LOW, Status.NEW, owner)));

        assertThatThrownBy(() -> taskService.delete(1L, stranger))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not your task");

        verify(taskRepository, never()).deleteById(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void delete_asAdmin_shouldDeleteForeignTask() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Buy milk", Priority.LOW, Status.NEW, owner)));

        taskService.delete(1L, admin);

        verify(taskRepository).deleteById(1L);
        verify(auditService).logAction("DELETE", 1L);
    }

    @Test
    void delete_whenTaskHasNoOwner_shouldAllowOnlyAdmin() {
        Task legacy = buildTask(1L, "Legacy", Priority.LOW, Status.NEW, null);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> taskService.delete(1L, owner)).isInstanceOf(AccessDeniedException.class);
        taskService.delete(1L, admin);

        verify(taskRepository).deleteById(1L);
    }

    @Test
    void delete_whenNotExists_shouldThrowNotFoundAndDeleteNothing() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(999L, owner))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");

        verify(taskRepository, never()).deleteById(any());
        verifyNoInteractions(auditService);
    }

    // ---------- isOwner() для @PreAuthorize ----------

    @Test
    void isOwner_whenEmailMatchesOwner_shouldReturnTrueIgnoringCase() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Task", Priority.LOW, Status.NEW, owner)));

        assertThat(taskService.isOwner(1L, "OWNER@test.com")).isTrue();
    }

    @Test
    void isOwner_whenDifferentEmail_shouldReturnFalse() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(buildTask(1L, "Task", Priority.LOW, Status.NEW, owner)));

        assertThat(taskService.isOwner(1L, "stranger@test.com")).isFalse();
    }

    @Test
    void isOwner_whenTaskMissingOrHasNoOwner_shouldReturnFalse() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());
        when(taskRepository.findById(2L))
                .thenReturn(Optional.of(buildTask(2L, "Legacy", Priority.LOW, Status.NEW, null)));

        assertThat(taskService.isOwner(999L, "owner@test.com")).isFalse();
        assertThat(taskService.isOwner(2L, "owner@test.com")).isFalse();
    }

    // ---------- статистика, поиск, prototype ----------

    @Test
    void countByStatus_asAdmin_shouldContainAllStatusesIncludingZeroes() {
        List<Object[]> rows = List.of(new Object[]{Status.NEW, 3L}, new Object[]{Status.DONE, 2L});
        when(taskRepository.countGroupedByStatus()).thenReturn(rows);

        Map<String, Long> result = taskService.countByStatus(admin);

        assertThat(result).containsExactly(
                entry("NEW", 3L),
                entry("IN_PROGRESS", 0L),
                entry("DONE", 2L));
    }

    @Test
    void countByStatus_asUser_shouldCountOnlyOwnTasks() {
        when(taskRepository.findAllByOwner(owner)).thenReturn(List.of(
                buildTask(1L, "a", Priority.LOW, Status.NEW, owner),
                buildTask(2L, "b", Priority.LOW, Status.NEW, owner),
                buildTask(3L, "c", Priority.LOW, Status.DONE, owner)));

        Map<String, Long> result = taskService.countByStatus(owner);

        assertThat(result).containsExactly(
                entry("NEW", 2L),
                entry("IN_PROGRESS", 0L),
                entry("DONE", 1L));
        verify(taskRepository, never()).countGroupedByStatus();
    }

    @Test
    void search_asUser_shouldReturnOnlyOwnMatches() {
        Task own = buildTask(1L, "Buy milk", Priority.LOW, Status.NEW, owner);
        Task foreign = buildTask(2L, "Buy bread", Priority.LOW, Status.NEW, stranger);
        when(taskRepository.searchByKeyword("buy")).thenReturn(List.of(own, foreign));

        List<TaskResponse> result = taskService.search("  buy ", owner);

        assertThat(result).extracting(TaskResponse::getTitle).containsExactly("Buy milk");
    }

    @Test
    void search_asAdmin_shouldReturnAllMatches() {
        Task own = buildTask(1L, "Buy milk", Priority.LOW, Status.NEW, owner);
        Task foreign = buildTask(2L, "Buy bread", Priority.LOW, Status.NEW, stranger);
        when(taskRepository.searchByKeyword("buy")).thenReturn(List.of(own, foreign));

        List<TaskResponse> result = taskService.search("buy", admin);

        assertThat(result).hasSize(2);
    }

    @Test
    void search_whenKeywordBlank_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> taskService.search("  ", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void getCreatedToday_shouldQueryOneDayRange() {
        when(taskRepository.findCreatedBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        taskService.getCreatedToday(admin);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskRepository).findCreatedBetween(from.capture(), to.capture());
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(1));
        assertThat(from.getValue().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void getStats_shouldReturnNewPrototypeInstanceOnEveryCall() {
        when(statsProvider.getObject()).thenReturn(new TaskStatsService(), new TaskStatsService());

        TaskStatsService first = taskService.getStats();
        TaskStatsService second = taskService.getStats();

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    // ---------- вспомогательные методы (без дублирования создания объектов) ----------

    private static User buildUser(Long id, String email, Role role) {
        User user = new User(email, "hash", role);
        user.setId(id);
        return user;
    }

    private static Task buildTask(Long id, String title, Priority priority, Status status, User owner) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setPriority(priority);
        task.setStatus(status);
        task.setOwner(owner);
        return task;
    }

    private static TaskRequest buildRequest(String title, Priority priority) {
        TaskRequest request = new TaskRequest();
        request.setTitle(title);
        request.setPriority(priority);
        return request;
    }
}
