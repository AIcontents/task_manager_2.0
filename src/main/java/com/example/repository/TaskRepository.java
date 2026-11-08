package com.example.repository;

import com.example.model.Status;
import com.example.model.Task;

import java.util.List;
import java.util.Optional;

/** Хранилище задач. В Части 1 есть две реализации - см. профили dev и prod. */
public interface TaskRepository {

    List<Task> findAll();

    Optional<Task> findById(Long id);

    Task save(Task task);

    void delete(Long id);

    /** Меняет статус задачи; пустой Optional, если задачи с таким id нет. */
    Optional<Task> updateStatus(Long id, Status status);

    long count();
}
