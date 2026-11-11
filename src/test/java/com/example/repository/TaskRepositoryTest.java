package com.example.repository;

import com.example.model.Priority;
import com.example.model.Role;
import com.example.model.Status;
import com.example.model.Task;
import com.example.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest поднимает только JPA-слой и встроенную H2 (без контроллеров и сервисов):
 * быстрее @SpringBootTest, но медленнее чистых юнит-тестов с Mockito.
 * Каждый тест выполняется в транзакции, которая откатывается в конце, - БД остаётся чистой.
 */
@DataJpaTest
@ActiveProfiles("test") // схему создаёт Flyway (db/migration), без демо-данных профиля dev
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional // для наглядности: @DataJpaTest и так оборачивает каждый тест в откатываемую транзакцию
    void save_shouldPersistTask() {
        Task task = new Task();
        task.setTitle("Test");
        task.setStatus(Status.NEW);
        task.setPriority(Priority.HIGH);

        Task saved = taskRepository.save(task);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(taskRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByStatusAndPriority_shouldReturnOnlyMatchingTasks() {
        taskRepository.save(newTask("Нужная", null, Priority.HIGH, Status.NEW));
        taskRepository.save(newTask("Другой статус", null, Priority.HIGH, Status.DONE));
        taskRepository.save(newTask("Другой приоритет", null, Priority.LOW, Status.NEW));

        List<Task> result = taskRepository.findByStatusAndPriority(Status.NEW, Priority.HIGH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Нужная");
    }

    @Test
    void findByStatus_withPageable_shouldReturnPageWithMetadata() {
        taskRepository.save(newTask("1", null, Priority.LOW, Status.NEW));
        taskRepository.save(newTask("2", null, Priority.LOW, Status.NEW));
        taskRepository.save(newTask("3", null, Priority.LOW, Status.NEW));
        taskRepository.save(newTask("4", null, Priority.LOW, Status.DONE));

        Page<Task> page = taskRepository.findByStatus(Status.NEW, PageRequest.of(0, 2, Sort.by("id")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void countGroupedByStatus_shouldGroupTasksByStatus() {
        taskRepository.save(newTask("a", null, Priority.LOW, Status.NEW));
        taskRepository.save(newTask("b", null, Priority.LOW, Status.NEW));
        taskRepository.save(newTask("c", null, Priority.LOW, Status.DONE));

        Map<String, Long> counts = taskRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> ((Number) row[1]).longValue()));

        assertThat(counts).containsEntry("NEW", 2L).containsEntry("DONE", 1L);
    }

    @Test
    void searchByKeyword_shouldFindInTitleOrDescriptionIgnoringCase() {
        taskRepository.save(newTask("Купить молоко", null, Priority.LOW, Status.NEW));
        taskRepository.save(newTask("Позвонить", "Нужно КУПИТЬ хлеб", Priority.LOW, Status.NEW));
        taskRepository.save(newTask("Совсем другое", "Без ключевого слова", Priority.LOW, Status.NEW));

        List<Task> result = taskRepository.searchByKeyword("купить");

        assertThat(result).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Купить молоко", "Позвонить");
    }

    @Test
    void findAllByOwner_shouldReturnOnlyTasksOfThatOwner() {
        User alice = userRepository.save(new User("alice@test.com", "hash", Role.ROLE_USER));
        User bob = userRepository.save(new User("bob@test.com", "hash", Role.ROLE_USER));
        taskRepository.save(ownedTask("A1", alice, Status.NEW));
        taskRepository.save(ownedTask("A2", alice, Status.DONE));
        taskRepository.save(ownedTask("B1", bob, Status.NEW));

        assertThat(taskRepository.findAllByOwner(alice)).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("A1", "A2");
        assertThat(taskRepository.findAllByOwner(bob)).extracting(Task::getTitle).containsExactly("B1");
    }

    @Test
    void findByOwnerAndStatus_shouldFilterByOwnerAndPage() {
        User alice = userRepository.save(new User("alice@test.com", "hash", Role.ROLE_USER));
        User bob = userRepository.save(new User("bob@test.com", "hash", Role.ROLE_USER));
        taskRepository.save(ownedTask("A1", alice, Status.NEW));
        taskRepository.save(ownedTask("A2", alice, Status.NEW));
        taskRepository.save(ownedTask("A3", alice, Status.DONE));
        taskRepository.save(ownedTask("B1", bob, Status.NEW));

        Page<Task> page = taskRepository.findByOwnerAndStatus(alice, Status.NEW, PageRequest.of(0, 10, Sort.by("id")));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("A1", "A2");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private static Task ownedTask(String title, User owner, Status status) {
        Task task = newTask(title, null, Priority.MEDIUM, status);
        task.setOwner(owner);
        return task;
    }

    private static Task newTask(String title, String description, Priority priority, Status status) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStatus(status);
        return task;
    }
}
