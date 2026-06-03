ALTER TABLE task
    ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE task_column_history
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(32) NOT NULL DEFAULT 'COLUMN_CHANGE';

UPDATE task_column_history
SET event_type = 'CREATED'
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY moved_at ASC, id ASC) AS row_num
        FROM task_column_history
    ) ranked
    WHERE row_num = 1
);
