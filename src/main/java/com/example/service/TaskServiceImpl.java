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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> SORTABLE_FIELDS =
            List.of("id", "title", "description", "priority", "status", "createdAt");

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
    public Page<Task> getAllTasks(String status, String priority, int page, int size, String sort) {
        Status statusFilter = parseEnum(Status.class, status, "status");
        Priority priorityFilter = parseEnum(Priority.class, priority, "priority");
        Pageable pageable = buildPageable(page, size, sort);

        // Derived queries: Spring Data сам строит SQL по имени метода
        if (statusFilter != null && priorityFilter != null) {
            return taskRepository.findByStatusAndPriority(statusFilter, priorityFilter, pageable);
        }
        if (statusFilter != null) {
            return taskRepository.findByStatus(statusFilter, pageable);
        }
        if (priorityFilter != null) {
            return taskRepository.findByPriority(priorityFilter, pageable);
        }
        return taskRepository.findAll(pageable);
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
        Task task = getTask(id);
        task.setStatus(status);
        return taskRepository.save(task);
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
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }

    @Override
    public Map<String, Long> countByStatus() {
        // Сначала нули по всем статусам, чтобы в ответе всегда были все ключи
        Map<String, Long> result = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            result.put(status.name(), 0L);
        }
        // JPQL: SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status
        for (Object[] row : taskRepository.countGroupedByStatus()) {
            result.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }

    @Override
    public List<Task> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Параметр keyword не может быть пустым");
        }
        return taskRepository.searchByKeyword(keyword.trim());
    }

    @Override
    public List<Task> getTasksCreatedToday() {
        LocalDate today = LocalDate.now();
        return taskRepository.findCreatedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    @Override
    public List<Task> getTasksByStatus(Status status) {
        return taskRepository.findByStatus(status);
    }

    @Override
    public List<Task> getTasksByPriority(Priority priority) {
        return taskRepository.findByPriority(priority);
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

    private static Pageable buildPageable(int page, int size, String sort) {
        if (page < 0) {
            throw new IllegalArgumentException("Параметр page не может быть отрицательным");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Параметр size должен быть от 1 до " + MAX_PAGE_SIZE);
        }
        String[] parts = (sort == null || sort.isBlank() ? "id" : sort).split(",");
        String property = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(property)) {
            throw new IllegalArgumentException("Сортировка по полю '" + property
                    + "' не поддерживается. Допустимые поля: " + String.join(", ", SORTABLE_FIELDS));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String requested = parts[1].trim().toLowerCase(Locale.ROOT);
            if (requested.equals("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!requested.equals("asc")) {
                throw new IllegalArgumentException("Направление сортировки должно быть asc или desc");
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
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
