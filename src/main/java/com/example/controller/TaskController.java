package com.example.controller;

import com.example.dto.PageResponse;
import com.example.model.Task;
import com.example.service.TaskService;
import org.springframework.http.ResponseEntity;
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
 * REST API задач. В контроллере нет бизнес-логики: только разбор HTTP-запроса,
 * вызов сервиса и выбор статуса ответа. Ошибки превращает в JSON GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService; // constructor injection
    }

    /**
     * GET /tasks?page=0&size=10&sort=id - постранично; необязательные фильтры status и priority:
     * ?status=DONE, ?priority=HIGH, ?status=NEW&priority=LOW. Ответ: content, totalElements, totalPages ...
     */
    @GetMapping
    public PageResponse<Task> getAll(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(defaultValue = "id") String sort) {
        return PageResponse.from(taskService.getAllTasks(status, priority, page, size, sort));
    }

    /** GET /tasks/search?keyword=купить - поиск по title и description (JPQL). */
    @GetMapping("/search")
    public List<Task> search(@RequestParam String keyword) {
        return taskService.searchByKeyword(keyword);
    }

    /** GET /tasks/today - задачи, созданные сегодня (JPQL по createdAt). */
    @GetMapping("/today")
    public List<Task> createdToday() {
        return taskService.getTasksCreatedToday();
    }

    /** Литеральные маршруты (/stats, /search, /today) имеют приоритет над шаблоном /tasks/{id}. */
    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        return taskService.countByStatus();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getById(@PathVariable Long id) {
        // 404 выбрасывает сервис (TaskNotFoundException), JSON формирует GlobalExceptionHandler
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @PostMapping
    public ResponseEntity<Task> create(@RequestBody Task request) {
        Task created = taskService.createTask(request.getTitle(), request.getDescription(), request.getPriority());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created); // 201 Created + заголовок Location
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> update(@PathVariable Long id, @RequestBody Task request) {
        return ResponseEntity.ok(taskService.updateTask(id, request.getTitle(), request.getDescription(),
                request.getPriority(), request.getStatus()));
    }

    /** Тело запроса: {"status":"DONE"} */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(taskService.updateStatus(id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build(); // 204, тела нет
    }
}
