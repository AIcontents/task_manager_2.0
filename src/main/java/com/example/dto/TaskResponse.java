package com.example.dto;

import com.example.model.Task;

import java.time.LocalDateTime;

/** Выходной DTO: то, что клиент видит в JSON. priority и status - строки (NEW, HIGH ...). */
public class TaskResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final String priority;
    private final String status;
    private final LocalDateTime createdAt;

    public TaskResponse(Long id, String title, String description, String priority,
                        String status, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority() != null ? task.getPriority().name() : null,
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "TaskResponse{id=" + id
                + ", title='" + title + '\''
                + ", priority=" + priority
                + ", status=" + status + '}';
    }
}
