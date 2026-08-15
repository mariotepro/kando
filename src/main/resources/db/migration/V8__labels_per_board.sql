-- ─── Per-board labels ──────────────────────────────────────────────────────
-- board_id starts nullable, same rationale as board_column.board_id in V7: legacy
-- labels are adopted into a user's first board lazily by the application, the
-- first time that board is resolved after the update.
ALTER TABLE label ADD COLUMN board_id BIGINT REFERENCES board(id);

-- Labels used to be unique by name across the whole app; now they only need to
-- be unique within a board, so two boards can each have their own "urgente".
-- The old single-column UNIQUE constraint from V1 is dropped in V9 (its
-- auto-generated name differs between Postgres and H2, so it's located there
-- via information_schema instead of being hardcoded).
CREATE UNIQUE INDEX label_board_id_name_key ON label (board_id, name);
