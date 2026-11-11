package com.example.service;

import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.model.Status;
import com.example.model.User;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Бизнес-логика работы с задачами. Контроллеры содержат только вызовы этого интерфейса.
 * <p>
 * Текущий пользователь передаётся параметром (контроллер получает его через @AuthenticationPrincipal),
 * поэтому сервис не обращается к SecurityContextHolder и легко тестируется. Правило доступа:
 * ADMIN работает со всеми задачами, обычный пользователь - только со своими;
 * нарушение приводит к AccessDeniedException("Not your task") -> 403.
 */
public interface TaskService {

    /** Владелец новой задачи - currentUser. */
    TaskResponse create(TaskRequest request, User currentUser);

    /** Все задачи, доступные пользователю (ADMIN - все, USER - свои). */
    List<TaskResponse> getAll(User currentUser);

    /**
     * Постраничный список доступных пользователю задач с необязательными фильтрами
     * (null или пустая строка - фильтр не применяется). Значения status и priority регистронезависимы;
     * sort - поле или "поле,desc". Неверные значения приводят к IllegalArgumentException (HTTP 400).
     */
    Page<TaskResponse> getAll(String status, String priority, int page, int size, String sort, User currentUser);

    /** Только задачи самого пользователя (GET /tasks/my) - даже для ADMIN. */
    List<TaskResponse> getMyTasks(User currentUser);

    /** Пустой Optional - задачи нет; AccessDeniedException - задача чужая. */
    Optional<TaskResponse> findById(Long id, User currentUser);

    /** Как findById, но бросает TaskNotFoundException (HTTP 404), если задачи нет. */
    TaskResponse getOrThrow(Long id, User currentUser);

    /** Полное обновление (PUT). Статус не меняется, если в запросе он не указан. Пустой Optional - задачи нет. */
    Optional<TaskResponse> update(Long id, TaskRequest request, User currentUser);

    /** Меняет только статус. Пустой Optional - задачи нет. */
    Optional<TaskResponse> updateStatus(Long id, Status status, User currentUser);

    /** TaskNotFoundException - задачи нет; AccessDeniedException("Not your task") - не владелец и не ADMIN. */
    void delete(Long id, User currentUser);

    /** Используется в SpEL: @PreAuthorize("... or @taskService.isOwner(#id, authentication.name)"). */
    boolean isOwner(Long taskId, String email);

    /** Количество доступных пользователю задач по статусам: {"NEW": 3, "IN_PROGRESS": 1, "DONE": 2}. */
    Map<String, Long> countByStatus(User currentUser);

    /** Поиск по ключевому слову в title или description среди доступных задач. */
    List<TaskResponse> search(String keyword, User currentUser);

    /** Доступные пользователю задачи, созданные сегодня. */
    List<TaskResponse> getCreatedToday(User currentUser);

    /** Каждый вызов возвращает новый экземпляр prototype-бина TaskStatsService. */
    TaskStatsService getStats();
}
