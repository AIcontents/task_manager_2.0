-- V5: владелец задачи. Колонка допускает NULL: у задач, созданных до появления пользователей, владельца нет.
ALTER TABLE tasks ADD COLUMN owner_id BIGINT;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_tasks_owner_id ON tasks (owner_id);
