package com.example.service;

import com.example.annotation.Loggable;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("taskService") // имя бина используется в SpEL: @taskService.isOwner(...)
@Transactional // все методы транзакционны (read-write) по умолчанию; для чтения переопределено ниже
public class TaskServiceImpl implements TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> SORTABLE_FIELDS =
            List.of("id", "title", "description", "priority", "status", "createdAt");

    private final TaskRepository taskRepository;
    private final AppProperties appProperties;
    // ObjectProvider, а не прямое внедрение: иначе prototype-бин был бы создан один раз
    // при создании singleton-сервиса, и UUID всегда был бы одинаковым.
    private final ObjectProvider<TaskStatsService> statsProvider;
    // Отдельный бин: вызов через контейнер (прокси) открывает собственную транзакцию REQUIRES_NEW
    private final AuditService auditService;

    public TaskServiceImpl(TaskRepository taskRepository,
                           AppProperties appProperties,
                           ObjectProvider<TaskStatsService> statsProvider,
                           AuditService auditService) {
        this.taskRepository = taskRepository;
        this.appProperties = appProperties;
        this.statsProvider = statsProvider;
        this.auditService = auditService;
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
    public TaskResponse create(TaskRequest request, User currentUser) {
        requireTitle(request.getTitle());
        if (taskRepository.count() >= appProperties.getMaxTasks()) {
            throw new TaskLimitExceededException(appProperties.getMaxTasks());
        }
        Task task = toEntity(request);
        task.setOwner(currentUser);
        Task saved = taskRepository.save(task);
        // REQUIRES_NEW: запись аудита коммитится независимо от основной транзакции
        auditService.logAction("CREATE", saved.getId());
        return TaskResponse.from(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TaskResponse> getAll(User currentUser) {
        return visibleTasks(currentUser).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<TaskResponse> getAll(String status, String priority, int page, int size, String sort,
                                     User currentUser) {
        Status statusFilter = parseEnum(Status.class, status, "status");
        Priority priorityFilter = parseEnum(Priority.class, priority, "priority");
        Pageable pageable = buildPageable(page, size, sort);
        return findPage(currentUser, statusFilter, priorityFilter, pageable).map(TaskResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TaskResponse> getMyTasks(User currentUser) {
        if (currentUser == null) {
            return List.of();
        }
        return taskRepository.findAllByOwner(currentUser).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<TaskResponse> findById(Long id, User currentUser) {
        return taskRepository.findById(id).map(task -> {
            requireAccess(task, currentUser);
            return TaskResponse.from(task);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public TaskResponse getOrThrow(Long id, User currentUser) {
        return findById(id, currentUser).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Loggable("Обновление задачи")
    @Override
    public Optional<TaskResponse> update(Long id, TaskRequest request, User currentUser) {
        return taskRepository.findById(id).map(task -> {
            requireAccess(task, currentUser);
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
    public Optional<TaskResponse> updateStatus(Long id, Status status, User currentUser) {
        return taskRepository.findById(id).map(task -> {
            requireAccess(task, currentUser);
            task.setStatus(status);
            return TaskResponse.from(taskRepository.save(task));
        });
    }

    @Loggable("Удаление задачи")
    @Override
    public void delete(Long id, User currentUser) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        requireAccess(task, currentUser); // не владелец и не ADMIN -> AccessDeniedException("Not your task")
        taskRepository.deleteById(id);
        auditService.logAction("DELETE", id);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isOwner(Long taskId, String email) {
        return taskRepository.findById(taskId)
                .map(task -> task.getOwner() != null && task.getOwner().getEmail().equalsIgnoreCase(email))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Long> countByStatus(User currentUser) {
        // Сначала нули по всем статусам, чтобы в ответе всегда были все ключи
        Map<String, Long> result = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            result.put(status.name(), 0L);
        }
        if (isAdmin(currentUser)) {
            // JPQL: SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status
            for (Object[] row : taskRepository.countGroupedByStatus()) {
                result.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
        } else if (currentUser != null) {
            for (Task task : taskRepository.findAllByOwner(currentUser)) {
                result.merge(task.getStatus().name(), 1L, Long::sum);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public List<TaskResponse> search(String keyword, User currentUser) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Параметр keyword не может быть пустым");
        }
        return taskRepository.searchByKeyword(keyword.trim()).stream()
                .filter(task -> canAccess(task, currentUser))
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<TaskResponse> getCreatedToday(User currentUser) {
        LocalDate today = LocalDate.now();
        return taskRepository.findCreatedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()).stream()
                .filter(task -> canAccess(task, currentUser))
                .map(TaskResponse::from)
                .toList();
    }

    /** К БД не обращается - транзакция не нужна (NOT_SUPPORTED приостанавливает внешнюю, если она есть). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public TaskStatsService getStats() {
        return statsProvider.getObject();
    }

    private List<Task> visibleTasks(User user) {
        if (user == null) {
            return List.of();
        }
        return isAdmin(user) ? taskRepository.findAll() : taskRepository.findAllByOwner(user);
    }

    /** Derived queries: ADMIN видит все задачи, обычный пользователь - только свои. */
    private Page<Task> findPage(User user, Status status, Priority priority, Pageable pageable) {
        if (user == null) {
            return Page.empty(pageable);
        }
        if (isAdmin(user)) {
            if (status != null && priority != null) {
                return taskRepository.findByStatusAndPriority(status, priority, pageable);
            }
            if (status != null) {
                return taskRepository.findByStatus(status, pageable);
            }
            if (priority != null) {
                return taskRepository.findByPriority(priority, pageable);
            }
            return taskRepository.findAll(pageable);
        }
        if (status != null && priority != null) {
            return taskRepository.findByOwnerAndStatusAndPriority(user, status, priority, pageable);
        }
        if (status != null) {
            return taskRepository.findByOwnerAndStatus(user, status, pageable);
        }
        if (priority != null) {
            return taskRepository.findByOwnerAndPriority(user, priority, pageable);
        }
        return taskRepository.findByOwner(user, pageable);
    }

    private static boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ROLE_ADMIN;
    }

    private static boolean canAccess(Task task, User user) {
        if (user == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        return task.getOwner() != null && Objects.equals(task.getOwner().getId(), user.getId());
    }

    private static void requireAccess(Task task, User user) {
        if (!canAccess(task, user)) {
            throw new AccessDeniedException("Not your task");
        }
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
