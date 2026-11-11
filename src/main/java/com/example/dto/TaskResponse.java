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
    private final String ownerEmail;

    public TaskResponse(Long id, String title, String description, String priority,
                        String status, LocalDateTime createdAt, String ownerEmail) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
        this.ownerEmail = ownerEmail;
    }

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority() != null ? task.getPriority().name() : null,
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getCreatedAt(),
                task.getOwner() != null ? task.getOwner().getEmail() : null);
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

    /** Email владельца задачи; null, если у задачи нет владельца. */
    public String getOwnerEmail() {
        return ownerEmail;
    }

    @Override
    public String toString() {
        return "TaskResponse{id=" + id
                + ", title='" + title + '\''
                + ", priority=" + priority
                + ", status=" + status + '}';
    }
}
