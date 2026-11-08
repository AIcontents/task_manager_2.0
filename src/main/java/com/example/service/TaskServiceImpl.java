package com.example.service;

import com.example.config.AppProperties;
import com.example.exception.TaskLimitExceededException;
import com.example.exception.TaskNotFoundException;
import com.example.model.Priority;
import com.example.model.Status;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final AppProperties appProperties;
    // ObjectProvider, а не прямое внедрение: иначе prototype-бин был бы создан один раз
    // при создании singleton-сервиса, и UUID всегда был бы одинаковым.
    private final ObjectProvider<TaskStatsService> statsProvider;

    public TaskServiceImpl(TaskRepository taskRepository,
                           AppProperties appProperties,
                           ObjectProvider<TaskStatsService> statsProvider) {
        this.taskRepository = taskRepository;
        this.appProperties = appProperties;
        this.statsProvider = statsProvider;
    }

    @PostConstruct
    public void init() {
        System.out.println("TaskService инициализирован");
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("Завершение работы. Задач в хранилище: " + taskRepository.count());
    }

    @Override
    public Task createTask(String title, String description, Priority priority) {
        requireTitle(title);
        if (taskRepository.count() >= appProperties.getMaxTasks()) {
            throw new TaskLimitExceededException(appProperties.getMaxTasks());
        }
        Priority effectivePriority = priority != null ? priority : appProperties.getDefaultPriority();
        return taskRepository.save(new Task(title.trim(), description, effectivePriority));
    }

    @Override
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    @Override
    public List<Task> getAllTasks(String status, String priority) {
        Status statusFilter = parseEnum(Status.class, status, "status");
        Priority priorityFilter = parseEnum(Priority.class, priority, "priority");
        return taskRepository.findAll().stream()
                .filter(task -> statusFilter == null || task.getStatus() == statusFilter)
                .filter(task -> priorityFilter == null || task.getPriority() == priorityFilter)
                .toList();
    }

    @Override
    public Task getTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    public Task updateTask(Long id, String title, String description, Priority priority, Status status) {
        Task task = getTask(id);
        requireTitle(title);
        task.setTitle(title.trim());
        task.setDescription(description);
        task.setPriority(priority != null ? priority : appProperties.getDefaultPriority());
        if (status != null) {
            task.setStatus(status);
        }
        return taskRepository.save(task);
    }

    @Override
    public Task updateStatus(Long id, Status status) {
        return taskRepository.updateStatus(id, status).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    public Task updateStatus(Long id, String status) {
        Status parsed = parseEnum(Status.class, status, "status");
        if (parsed == null) {
            throw new IllegalArgumentException("Поле status обязательно. Допустимые значения: "
                    + allowedValues(Status.class));
        }
        return updateStatus(id, parsed);
    }

    @Override
    public void deleteTask(Long id) {
        if (taskRepository.findById(id).isEmpty()) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.delete(id);
    }

    @Override
    public Map<String, Long> countByStatus() {
        // Сначала нули по всем статусам, чтобы в ответе всегда были все ключи
        Map<String, Long> result = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            result.put(status.name(), 0L);
        }
        taskRepository.findAll().stream()
                .collect(Collectors.groupingBy(task -> task.getStatus().name(), Collectors.counting()))
                .forEach(result::put);
        return result;
    }

    @Override
    public List<Task> getTasksByStatus(Status status) {
        return taskRepository.findAll().stream()
                .filter(task -> task.getStatus() == status)
                .toList();
    }

    @Override
    public List<Task> getTasksByPriority(Priority priority) {
        return taskRepository.findAll().stream()
                .filter(task -> task.getPriority() == priority)
                .toList();
    }

    @Override
    public TaskStatsService getStats() {
        return statsProvider.getObject();
    }

    private static void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Название задачи не может быть пустым");
        }
    }

    /** Строка -> enum без учёта регистра; null и пустая строка дают null (фильтр не задан). */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестное значение поля " + field + ": '" + value
                    + "'. Допустимые значения: " + allowedValues(type));
        }
    }

    private static <E extends Enum<E>> String allowedValues(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
