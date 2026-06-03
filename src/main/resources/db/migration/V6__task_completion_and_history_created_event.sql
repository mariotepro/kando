ALTER TABLE task
    ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE task_column_history
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(32) NOT NULL DEFAULT 'COLUMN_CHANGE';

WITH ranked_history AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY moved_at ASC, id ASC) AS row_num
    FROM task_column_history
)
UPDATE task_column_history history
SET event_type = CASE
    WHEN ranked_history.row_num = 1 THEN 'CREATED'
    ELSE 'COLUMN_CHANGE'
END
FROM ranked_history
WHERE history.id = ranked_history.id
  AND history.event_type IS DISTINCT FROM CASE
      WHEN ranked_history.row_num = 1 THEN 'CREATED'
      ELSE 'COLUMN_CHANGE'
  END;
