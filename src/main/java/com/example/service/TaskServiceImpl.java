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

import java.util.List;

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
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Название задачи не может быть пустым");
        }
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
    public Task getTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    public Task updateStatus(Long id, Status status) {
        return taskRepository.updateStatus(id, status).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    public void deleteTask(Long id) {
        if (taskRepository.findById(id).isEmpty()) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.delete(id);
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
}
