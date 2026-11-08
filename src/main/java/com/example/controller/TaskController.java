package com.example.controller;

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

    /** GET /tasks, GET /tasks?status=DONE, GET /tasks?priority=HIGH, GET /tasks?status=NEW&priority=LOW */
    @GetMapping
    public List<Task> getAll(@RequestParam(required = false) String status,
                             @RequestParam(required = false) String priority) {
        return taskService.getAllTasks(status, priority);
    }

    /** Литеральный маршрут /tasks/stats имеет приоритет над шаблоном /tasks/{id}. */
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
