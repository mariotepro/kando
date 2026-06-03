-- Track every column transition per task
CREATE TABLE task_column_history (
    id          BIGSERIAL    PRIMARY KEY,
    task_id     BIGINT       NOT NULL,
    column_id   BIGINT,
    column_name VARCHAR(255) NOT NULL,
    moved_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tch_task   FOREIGN KEY (task_id)   REFERENCES task(id)         ON DELETE CASCADE,
    CONSTRAINT fk_tch_column FOREIGN KEY (column_id) REFERENCES board_column(id) ON DELETE SET NULL
);

CREATE INDEX idx_tch_task_id ON task_column_history(task_id, moved_at);

-- Mark which columns represent "done" state (used for completion date in export)
ALTER TABLE board_column ADD COLUMN done BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE board_column SET done = TRUE WHERE LOWER(name) = 'hecho';
