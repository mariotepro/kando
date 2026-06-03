ALTER TABLE task
    ADD COLUMN parent_task_id BIGINT REFERENCES task(id) ON DELETE SET NULL;

CREATE INDEX idx_task_parent_task_id ON task(parent_task_id);
