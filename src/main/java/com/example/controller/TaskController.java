package com.example.controller;

import com.example.dto.PageResponse;
import com.example.dto.StatusUpdateRequest;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskNotFoundException;
import com.example.model.User;
import com.example.service.TaskService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST API задач. В контроллере нет бизнес-логики: разбор HTTP-запроса, проверка прав (@PreAuthorize),
 * вызов сервиса и выбор статуса ответа. Ошибки превращает в JSON GlobalExceptionHandler.
 * <p>
 * Текущий пользователь приходит через @AuthenticationPrincipal (его кладёт в SecurityContext JwtAuthFilter)
 * и передаётся в сервис параметром - сервис не знает про SecurityContextHolder.
 * <p>
 * Работают оба префикса: /tasks (ТЗ частей 2, 3, 9, 11) и /api/tasks (ТЗ частей 5, 6, 8).
 */
@RestController
@RequestMapping({"/api/tasks", "/tasks"})
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    /**
     * SpEL: ADMIN или владелец задачи. #id - параметр метода (имя доступно благодаря флагу компиляции -parameters),
     * @taskService - бин TaskServiceImpl, authentication.name - email из JWT.
     */
    private static final String ADMIN_OR_OWNER = "hasRole('ADMIN') or @taskService.isOwner(#id, authentication.name)";

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService; // constructor injection
    }

    /**
     * GET /tasks?page=0&size=10&sort=id - постранично; необязательные фильтры status и priority.
     * ADMIN видит все задачи, обычный пользователь - только свои.
     */
    @GetMapping
    public PageResponse<TaskResponse> getAll(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String priority,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(defaultValue = "id") String sort,
                                             @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return PageResponse.from(taskService.getAll(status, priority, page, size, sort, currentUser));
    }

    /** GET /tasks/my - только задачи текущего пользователя (и для ADMIN тоже). */
    @GetMapping("/my")
    public List<TaskResponse> getMy(@Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return taskService.getMyTasks(currentUser);
    }

    /** GET /tasks/search?keyword=купить - поиск по title и description (JPQL). */
    @GetMapping("/search")
    public List<TaskResponse> search(@RequestParam String keyword,
                                     @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return taskService.search(keyword, currentUser);
    }

    /** GET /tasks/today - задачи, созданные сегодня (JPQL по createdAt). */
    @GetMapping("/today")
    public List<TaskResponse> createdToday(@Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return taskService.getCreatedToday(currentUser);
    }

    /** Литеральные маршруты (/my, /stats, /search, /today) имеют приоритет над шаблоном /tasks/{id}. */
    @GetMapping("/stats")
    public Map<String, Long> getStats(@Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return taskService.countByStatus(currentUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getById(@PathVariable Long id,
                                                @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        // 404 выбрасывает сервис (TaskNotFoundException), JSON формирует GlobalExceptionHandler
        return ResponseEntity.ok(taskService.getOrThrow(id, currentUser));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request,
                                               @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        TaskResponse created = taskService.create(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created); // 201 Created + заголовок Location
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN_OR_OWNER)
    public ResponseEntity<TaskResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody TaskRequest request,
                                               @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return taskService.update(id, request, currentUser)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    /** Тело запроса: {"status":"DONE"} */
    @PatchMapping("/{id}/status")
    @PreAuthorize(ADMIN_OR_OWNER)
    public ResponseEntity<TaskResponse> updateStatus(@PathVariable Long id,
                                                     @Valid @RequestBody StatusUpdateRequest request,
                                                     @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        return taskService.updateStatus(id, request.getStatus(), currentUser)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    /** USER -> 403 для чужих задач, ADMIN -> 204; для своей задачи владелец тоже получает 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN_OR_OWNER)
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @Parameter(hidden = true) @AuthenticationPrincipal User currentUser) {
        taskService.delete(id, currentUser);
        return ResponseEntity.noContent().build(); // 204, тела нет
    }
}
