-- V2: дата создания. DEFAULT NOW() заполняет это поле и у уже существующих задач.
ALTER TABLE tasks ADD COLUMN created_at TIMESTAMP DEFAULT NOW() NOT NULL;
