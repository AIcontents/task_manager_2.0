package com.example.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет, что миграции Flyway применяются при старте и создают схему, которую ожидают сущности. */
@DataJpaTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrations_shouldBeAppliedInOrderAndSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(info -> info.getVersion().getVersion())
                .startsWith("1", "2", "3", "4", "5", "6");
        assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
    }

    @Test
    void tasksTable_shouldHaveAllEntityColumns() {
        List<String> columns = columnsOf("tasks");

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "status", "created_at", "owner_id");
    }

    @Test
    void auditLogsTable_shouldHaveAllEntityColumns() {
        List<String> columns = columnsOf("audit_logs");

        assertThat(columns).containsExactlyInAnyOrder("id", "action", "task_id", "created_at");
    }

    @Test
    void usersTable_shouldHaveAllEntityColumns() {
        assertThat(columnsOf("users")).containsExactlyInAnyOrder("id", "email", "password", "role");
    }

    @Test
    void refreshTokensTable_shouldHaveAllEntityColumns() {
        assertThat(columnsOf("refresh_tokens")).containsExactlyInAnyOrder("id", "token", "user_id", "expires_at");
    }

    @Test
    void testProfile_shouldStartWithEmptyTasksTable() {
        // демо-данные лежат в db/seed и подключаются только профилем dev
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class);

        assertThat(count).isZero();
    }

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = ?",
                String.class, table);
    }
}
