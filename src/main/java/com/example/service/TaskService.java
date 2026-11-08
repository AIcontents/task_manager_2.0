package com.example.service;

import com.example.model.Priority;
import com.example.model.Status;
import com.example.model.Task;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/** Бизнес-логика работы с задачами. Контроллеры содержат только вызовы этого интерфейса. */
public interface TaskService {

    Task createTask(String title, String description, Priority priority);

    List<Task> getAllTasks();

    /**
     * Постраничный список с необязательными фильтрами (null или пустая строка - фильтр не применяется).
     * Значения status и priority регистронезависимы; sort - поле или "поле,desc".
     * Неверные значения приводят к IllegalArgumentException (HTTP 400).
     */
    Page<Task> getAllTasks(String status, String priority, int page, int size, String sort);

    Task getTask(Long id);

    /** Полное обновление (PUT). Статус не меняется, если status == null. */
    Task updateTask(Long id, String title, String description, Priority priority, Status status);

    Task updateStatus(Long id, Status status);

    /** Вариант для PATCH /tasks/{id}/status: статус приходит строкой из JSON. */
    Task updateStatus(Long id, String status);

    void deleteTask(Long id);

    /** Количество задач по статусам: {"NEW": 3, "IN_PROGRESS": 1, "DONE": 2}. */
    Map<String, Long> countByStatus();

    /** Поиск по ключевому слову в title или description. */
    List<Task> searchByKeyword(String keyword);

    /** Задачи, созданные сегодня. */
    List<Task> getTasksCreatedToday();

    List<Task> getTasksByStatus(Status status);

    List<Task> getTasksByPriority(Priority priority);

    /** Каждый вызов возвращает новый экземпляр prototype-бина TaskStatsService. */
    TaskStatsService getStats();
}
