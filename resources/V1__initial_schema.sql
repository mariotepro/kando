-- Reference copy of the V1 migration for documentation purposes.
-- The authoritative file used at runtime is:
--   src/main/resources/db/migration/V1__initial_schema.sql

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE kando_user (
    id       BIGSERIAL    PRIMARY KEY,
    username VARCHAR(64)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── Labels ──────────────────────────────────────────────────────────────────
CREATE TABLE label (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(64)  NOT NULL UNIQUE,
    color VARCHAR(7)   NOT NULL DEFAULT '#6366f1'
);

-- ─── Columns ─────────────────────────────────────────────────────────────────
CREATE TABLE board_column (
    id       BIGSERIAL    PRIMARY KEY,
    name     VARCHAR(128) NOT NULL,
    position INTEGER      NOT NULL DEFAULT 0
);

-- ─── Tasks ───────────────────────────────────────────────────────────────────
CREATE TABLE task (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(512) NOT NULL,
    notes       TEXT,
    due_date    DATE,
    column_id   BIGINT       NOT NULL REFERENCES board_column(id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Task ↔ Label (many-to-many) ─────────────────────────────────────────────
CREATE TABLE task_label (
    task_id  BIGINT NOT NULL REFERENCES task(id)  ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES label(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, label_id)
);

-- ─── Default columns ─────────────────────────────────────────────────────────
INSERT INTO board_column (name, position) VALUES
    ('Hoy',        0),
    ('Planificado', 1),
    ('En espera',  2),
    ('Hecho',      3);
