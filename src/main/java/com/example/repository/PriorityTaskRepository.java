package com.example.repository;

import com.example.model.Task;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Активен в профиле prod: задачи возвращаются по приоритету HIGH -> MEDIUM -> LOW.
 * Хранение наследуется от InMemoryTaskRepository, меняется только порядок выдачи.
 * (@Profile и @Component не наследуются - на этом классе они объявлены отдельно.)
 */
@Component
@Profile("prod")
public class PriorityTaskRepository extends InMemoryTaskRepository {

    @Override
    public List<Task> findAll() {
        return super.findAll().stream()
                .sorted(Comparator.comparing(Task::getPriority).reversed())
                .toList();
    }
}
