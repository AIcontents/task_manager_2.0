package com.example.service;

import com.example.annotation.Loggable;
import com.example.config.AppProperties;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
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
import java.util.Optional;
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

    @Loggable("Создание задачи")
    @Override
    public TaskResponse create(TaskRequest request) {
        requireTitle(request.getTitle());
        if (taskRepository.count() >= appProperties.getMaxTasks()) {
            throw new TaskLimitExceededException(appProperties.getMaxTasks());
        }
        Task saved = taskRepository.save(toEntity(request));
        return TaskResponse.from(saved);
    }

    @Override
    public List<TaskResponse> getAll() {
        return taskRepository.findAll().stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Override
    public Page<TaskResponse> getAll(String status, String priority, int page, int size, String sort) {
        Status statusFilter = parseEnum(Status.class, status, "status");
        Priority priorityFilter = parseEnum(Priority.class, priority, "priority");
        Pageable pageable = buildPageable(page, size, sort);

        // Derived queries: Spring Data сам строит SQL по имени метода
        Page<Task> result;
        if (statusFilter != null && priorityFilter != null) {
            result = taskRepository.findByStatusAndPriority(statusFilter, priorityFilter, pageable);
        } else if (statusFilter != null) {
            result = taskRepository.findByStatus(statusFilter, pageable);
        } else if (priorityFilter != null) {
            result = taskRepository.findByPriority(priorityFilter, pageable);
        } else {
            result = taskRepository.findAll(pageable);
        }
        return result.map(TaskResponse::from);
    }

    @Override
    public Optional<TaskResponse> findById(Long id) {
        return taskRepository.findById(id).map(TaskResponse::from);
    }

    @Override
    public TaskResponse getOrThrow(Long id) {
        return findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Loggable("Обновление задачи")
    @Override
    public Optional<TaskResponse> update(Long id, TaskRequest request) {
        return taskRepository.findById(id).map(task -> {
            requireTitle(request.getTitle());
            task.setTitle(request.getTitle().trim());
            task.setDescription(request.getDescription());
            task.setPriority(request.getPriority() != null
                    ? request.getPriority()
                    : appProperties.getDefaultPriority());
            if (request.getStatus() != null) {
                task.setStatus(request.getStatus());
            }
            return TaskResponse.from(taskRepository.save(task));
        });
    }

    @Override
    public Optional<TaskResponse> updateStatus(Long id, Status status) {
        return taskRepository.findById(id).map(task -> {
            task.setStatus(status);
            return TaskResponse.from(taskRepository.save(task));
        });
    }

    @Loggable("Удаление задачи")
    @Override
    public void delete(Long id) {
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
    public List<TaskResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Параметр keyword не может быть пустым");
        }
        return taskRepository.searchByKeyword(keyword.trim()).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Override
    public List<TaskResponse> getCreatedToday() {
        LocalDate today = LocalDate.now();
        return taskRepository.findCreatedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Override
    public TaskStatsService getStats() {
        return statsProvider.getObject();
    }

    private Task toEntity(TaskRequest request) {
        Priority priority = request.getPriority() != null
                ? request.getPriority()
                : appProperties.getDefaultPriority();
        return new Task(request.getTitle().trim(), request.getDescription(), priority);
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
