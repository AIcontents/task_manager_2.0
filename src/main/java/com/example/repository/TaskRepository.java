package com.example.repository;

import com.example.model.Priority;
import com.example.model.Status;
import com.example.model.Task;
import com.example.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA сам создаёт реализацию интерфейса. Базовые методы (findAll, findById, save,
 * deleteById, count, findAll(Pageable) ...) уже есть в JpaRepository.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    // --- Derived queries: SQL генерируется по имени метода ---

    // SELECT * FROM tasks WHERE status = ?
    List<Task> findByStatus(Status status);

    // SELECT * FROM tasks WHERE priority = ?
    List<Task> findByPriority(Priority priority);

    // SELECT * FROM tasks WHERE status = ? AND priority = ?
    List<Task> findByStatusAndPriority(Status status, Priority priority);

    // SELECT * FROM tasks WHERE UPPER(title) LIKE UPPER('%?%')
    List<Task> findByTitleContainingIgnoreCase(String keyword);

    // SELECT COUNT(*) FROM tasks WHERE status = ?
    long countByStatus(Status status);

    // --- Те же фильтры с пагинацией ---

    Page<Task> findByStatus(Status status, Pageable pageable);

    Page<Task> findByPriority(Priority priority, Pageable pageable);

    Page<Task> findByStatusAndPriority(Status status, Priority priority, Pageable pageable);

    // --- Задачи владельца (обычный пользователь видит только свои) ---

    // SELECT * FROM tasks WHERE owner_id = ?
    List<Task> findAllByOwner(User owner);

    Page<Task> findByOwner(User owner, Pageable pageable);

    Page<Task> findByOwnerAndStatus(User owner, Status status, Pageable pageable);

    Page<Task> findByOwnerAndPriority(User owner, Priority priority, Pageable pageable);

    Page<Task> findByOwnerAndStatusAndPriority(User owner, Status status, Priority priority, Pageable pageable);

    // --- JPQL: работает с именами классов и полей (Task, t.status), а не таблиц и колонок ---

    /** GROUP BY статус: возвращает пары [status, count]. */
    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    /** Поиск по ключевому слову в title или description без учёта регистра. */
    @Query("SELECT t FROM Task t WHERE "
            + "LOWER(t.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR "
            + "LOWER(t.description) LIKE LOWER(CONCAT('%', :kw, '%'))")
    List<Task> searchByKeyword(@Param("kw") String keyword);

    /** Задачи, созданные в промежутке [startOfDay, startOfNextDay) - например, сегодня. */
    @Query("SELECT t FROM Task t WHERE t.createdAt >= :startOfDay AND t.createdAt < :startOfNextDay "
            + "ORDER BY t.createdAt")
    List<Task> findCreatedBetween(@Param("startOfDay") LocalDateTime startOfDay,
                                  @Param("startOfNextDay") LocalDateTime startOfNextDay);
}
