package com.example.service;

import com.example.model.Priority;
import com.example.model.Status;
import com.example.model.Task;

import java.util.List;

/** Бизнес-логика работы с задачами. */
public interface TaskService {

    Task createTask(String title, String description, Priority priority);

    List<Task> getAllTasks();

    Task getTask(Long id);

    Task updateStatus(Long id, Status status);

    void deleteTask(Long id);

    List<Task> getTasksByStatus(Status status);

    List<Task> getTasksByPriority(Priority priority);

    /** Каждый вызов возвращает новый экземпляр prototype-бина TaskStatsService. */
    TaskStatsService getStats();
}
