-- Oba Database Schema - Kaizen: One Sprint Series Per Board
-- V1_2_95__Kaizen_One_Sprint_Series_Per_Board.sql
--
-- A board belongs to at most one sprint series. Until now kaizen.sprint_series_boards
-- was unique only per (series, board), so a board could sit in two repeating series
-- and the job then created two sprints for it every rollover. This migration keeps,
-- per board, one series, switches off the enabled series that lose a board, removes
-- the losing rows and makes board_id unique. Sprints, rounds, board links and series
-- names are not touched.

-- =====================================================
-- 1. Rank the series that share a board
-- =====================================================

-- Rank 1 is kept: the enabled series first, then the one that ran most recently,
-- then the one whose latest live sprint started last, then the lowest id. A board
-- in one series gets rank 1 and is never touched.
CREATE TEMPORARY TABLE sprint_series_board_ranks ON COMMIT DROP AS
SELECT
    ssb.id AS row_id,
    ssb.series_id,
    ssb.board_id,
    ROW_NUMBER() OVER (
        PARTITION BY ssb.board_id
        ORDER BY s.enabled DESC, s.last_run_at DESC NULLS LAST, ls.latest_start DESC NULLS LAST, s.id
    ) AS rank
FROM kaizen.sprint_series_boards ssb
JOIN kaizen.sprint_series s ON s.id = ssb.series_id
LEFT JOIN LATERAL (
    SELECT MAX(sp.start_date) AS latest_start
    FROM kaizen.sprints sp
    WHERE sp.series_id = s.id AND sp.deleted = FALSE
) ls ON TRUE;

-- =====================================================
-- 2. Switch off the enabled series that lose a board
-- =====================================================

-- The message names the first lost board by board name and the series that keeps
-- it. next_creation_at is cleared because the job reads it only for enabled series.
UPDATE kaizen.sprint_series s
SET enabled = FALSE,
    next_creation_at = NULL,
    last_run_at = CURRENT_TIMESTAMP,
    last_run_status = 'FAILED',
    last_run_message = LEFT(
        'Switched off: board ' || lost.board_name || ' belongs to series ' || lost.kept_series_name,
        500
    ),
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT ON (r.series_id)
        r.series_id,
        b.name AS board_name,
        ks.name AS kept_series_name
    FROM sprint_series_board_ranks r
    JOIN kaizen.boards b ON b.id = r.board_id
    JOIN sprint_series_board_ranks kept ON kept.board_id = r.board_id AND kept.rank = 1
    JOIN kaizen.sprint_series ks ON ks.id = kept.series_id
    WHERE r.rank > 1
    ORDER BY r.series_id, b.name, b.id
) lost
WHERE s.id = lost.series_id
  AND s.enabled;

-- =====================================================
-- 3. Delete the losing rows
-- =====================================================

DELETE FROM kaizen.sprint_series_boards ssb
USING sprint_series_board_ranks r
WHERE ssb.id = r.row_id
  AND r.rank > 1;

-- =====================================================
-- 4. Make board_id unique
-- =====================================================

-- The unique index replaces the plain foreign key index on board_id.
-- sprint_series_boards_series_id_board_id_uk stays: it serves lookups by series_id.
DROP INDEX kaizen.sprint_series_boards_board_id_idx;

ALTER TABLE kaizen.sprint_series_boards
    ADD CONSTRAINT sprint_series_boards_board_id_uk UNIQUE (board_id);
