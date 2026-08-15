-- ─── Boards ──────────────────────────────────────────────────────────────────
CREATE TABLE board (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    owner_id   BIGINT       NOT NULL REFERENCES kando_user(id),
    position   INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- board_id starts nullable: at migration time there may be no user yet (fresh
-- install, admin created later via /setup) or an existing single-board install
-- with columns that predate boards. The application adopts these orphan
-- columns into a user's first board the first time they open /board.
ALTER TABLE board_column ADD COLUMN board_id BIGINT REFERENCES board(id);
