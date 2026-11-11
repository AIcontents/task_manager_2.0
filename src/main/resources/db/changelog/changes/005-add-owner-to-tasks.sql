--liquibase formatted sql

--changeset dev:005
ALTER TABLE tasks ADD COLUMN owner_id BIGINT;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_tasks_owner_id ON tasks (owner_id);
--rollback DROP INDEX idx_tasks_owner_id;
--rollback ALTER TABLE tasks DROP CONSTRAINT fk_tasks_owner;
--rollback ALTER TABLE tasks DROP COLUMN owner_id;
