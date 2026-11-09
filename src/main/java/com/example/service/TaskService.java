package com.example.service;

import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.model.Status;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Бизнес-логика работы с задачами. Контроллеры содержат только вызовы этого интерфейса. */
public interface TaskService {

    TaskResponse create(TaskRequest request);

    List<TaskResponse> getAll();

    /**
     * Постраничный список с необязательными фильтрами (null или пустая строка - фильтр не применяется).
     * Значения status и priority регистронезависимы; sort - поле или "поле,desc".
     * Неверные значения приводят к IllegalArgumentException (HTTP 400).
     */
    Page<TaskResponse> getAll(String status, String priority, int page, int size, String sort);

    Optional<TaskResponse> findById(Long id);

    /** Как findById, но бросает TaskNotFoundException (HTTP 404), если задачи нет. */
    TaskResponse getOrThrow(Long id);

    /** Полное обновление (PUT). Статус не меняется, если в запросе он не указан. Пустой Optional - задачи нет. */
    Optional<TaskResponse> update(Long id, TaskRequest request);

    /** Меняет только статус. Пустой Optional - задачи нет. */
    Optional<TaskResponse> updateStatus(Long id, Status status);

    /** Бросает TaskNotFoundException, если задачи с таким id нет. */
    void delete(Long id);

    /** Количество задач по статусам: {"NEW": 3, "IN_PROGRESS": 1, "DONE": 2}. */
    Map<String, Long> countByStatus();

    /** Поиск по ключевому слову в title или description. */
    List<TaskResponse> search(String keyword);

    /** Задачи, созданные сегодня. */
    List<TaskResponse> getCreatedToday();

    /** Каждый вызов возвращает новый экземпляр prototype-бина TaskStatsService. */
    TaskStatsService getStats();
}
