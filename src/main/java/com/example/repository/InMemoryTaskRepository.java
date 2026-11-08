package com.example.repository;

import com.example.model.Status;
import com.example.model.Task;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Хранение задач в обычном List. Активен в профиле dev.
 * {@code @Primary} - при наличии нескольких TaskRepository (например, dev,prod одновременно)
 * Spring выберет именно этот бин.
 */
@Component
@Primary
@Profile("dev")
public class InMemoryTaskRepository implements TaskRepository {

    private final List<Task> storage = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public synchronized List<Task> findAll() {
        return new ArrayList<>(storage);
    }

    @Override
    public synchronized Optional<Task> findById(Long id) {
        return storage.stream()
                .filter(task -> Objects.equals(task.getId(), id))
                .findFirst();
    }

    @Override
    public synchronized Task save(Task task) {
        if (task.getId() == null) {
            task.setId(sequence.incrementAndGet());
            storage.add(task);
            return task;
        }
        for (int i = 0; i < storage.size(); i++) {
            if (Objects.equals(storage.get(i).getId(), task.getId())) {
                storage.set(i, task);
                return task;
            }
        }
        storage.add(task);
        return task;
    }

    @Override
    public synchronized void delete(Long id) {
        storage.removeIf(task -> Objects.equals(task.getId(), id));
    }

    @Override
    public synchronized Optional<Task> updateStatus(Long id, Status status) {
        Optional<Task> found = findById(id);
        found.ifPresent(task -> task.setStatus(status));
        return found;
    }

    @Override
    public synchronized long count() {
        return storage.size();
    }
}
