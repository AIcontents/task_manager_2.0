-- Демо-данные ТОЛЬКО для профиля dev (папка db/seed подключается через spring.flyway.locations в профиле dev).
-- Repeatable-миграция (R__): применяется после всех версионных и повторно - только если изменить файл.
-- Данные намеренно не лежат в V1: схема не должна тащить за собой тестовые строки (в тестах и в docker их нет).
INSERT INTO tasks (title, description, priority, status) VALUES
    ('Изучить Flyway', 'Прочитать документацию и написать первую миграцию', 'HIGH', 'NEW'),
    ('Написать тесты', 'Юнит-тесты на JUnit 5 + Mockito', 'MEDIUM', 'NEW'),
    ('Выложить проект на GitHub', NULL, 'LOW', 'IN_PROGRESS');
