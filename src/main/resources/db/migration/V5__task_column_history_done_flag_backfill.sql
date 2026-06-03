ALTER TABLE task_column_history
    ADD COLUMN IF NOT EXISTS column_done BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE task_column_history history
SET column_done = column_ref.done
FROM board_column column_ref
WHERE history.column_id = column_ref.id
  AND history.column_done IS DISTINCT FROM column_ref.done;
