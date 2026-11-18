-- Oba Database Schema - Kaizen: Release Finished Sprint Series
-- V1_2_108__Kaizen_Release_Finished_Sprint_Series.sql
--
-- A takeover that closed the only sprint of a repeating series left that series
-- switched on with nothing to run. The job never picks such a series up again,
-- yet it kept its boards, so V1_2_95 could rank it above a live series that
-- shared a board, and switched the live one off. The application now switches a
-- series off when a user's change leaves it without a running or planned sprint.
-- This migration does the same for the series already in that state, then gives
-- each series that V1_2_95 switched off back its boards and switches it on again,
-- when it still has a running or planned sprint and no switched-on series holds
-- one of its boards.

-- =====================================================
-- 1. Switch off series without a running or planned sprint
-- =====================================================

-- The message matches SprintRecurrenceConstant.RUN_MESSAGE_NO_LIVE_SPRINT.
UPDATE kaizen.sprint_series s
SET enabled = FALSE,
    next_creation_at = NULL,
    last_run_at = CURRENT_TIMESTAMP,
    last_run_status = 'FAILED',
    last_run_message = 'no running or planned sprint left',
    updated_at = CURRENT_TIMESTAMP
WHERE s.enabled
  AND NOT EXISTS (
      SELECT 1
      FROM kaizen.sprints sp
      WHERE sp.series_id = s.id
        AND sp.deleted = FALSE
        AND sp.status IN ('ACTIVE', 'PLANNED')
  );

-- =====================================================
-- 2. Find the series V1_2_95 switched off that can run again
-- =====================================================

-- A candidate is a series still off with the message V1_2_95 wrote, which still
-- has a running or planned sprint. It wants every live board linked to those
-- sprints.
CREATE TEMPORARY TABLE sprint_series_restore_wants ON COMMIT DROP AS
SELECT DISTINCT s.id AS series_id, bs.board_id
FROM kaizen.sprint_series s
JOIN kaizen.sprints sp ON sp.series_id = s.id
    AND sp.deleted = FALSE
    AND sp.status IN ('ACTIVE', 'PLANNED')
JOIN kaizen.board_sprints bs ON bs.sprint_id = sp.id AND bs.deleted = FALSE
JOIN kaizen.boards b ON b.id = bs.board_id AND b.deleted = FALSE
WHERE NOT s.enabled
  AND s.last_run_status = 'FAILED'
  AND s.last_run_message LIKE 'Switched off: board % belongs to series %';

-- A candidate is restored only when none of its boards is held by a series that
-- is still on, and no other candidate wants one of its boards: the choice
-- between two live series stays with the user.
CREATE TEMPORARY TABLE sprint_series_restore ON COMMIT DROP AS
SELECT DISTINCT w.series_id
FROM sprint_series_restore_wants w
WHERE NOT EXISTS (
    SELECT 1
    FROM sprint_series_restore_wants mine
    JOIN kaizen.sprint_series_boards ssb ON ssb.board_id = mine.board_id AND ssb.series_id <> mine.series_id
    JOIN kaizen.sprint_series owner ON owner.id = ssb.series_id AND owner.enabled
    WHERE mine.series_id = w.series_id
)
AND NOT EXISTS (
    SELECT 1
    FROM sprint_series_restore_wants mine
    JOIN sprint_series_restore_wants other ON other.board_id = mine.board_id AND other.series_id <> mine.series_id
    WHERE mine.series_id = w.series_id
);

-- =====================================================
-- 3. Give the restored series their boards back
-- =====================================================

-- The board leaves the switched-off series that holds it, as the application
-- does when a series that is on claims a board.
DELETE FROM kaizen.sprint_series_boards ssb
USING sprint_series_restore_wants w
JOIN sprint_series_restore r ON r.series_id = w.series_id
WHERE ssb.board_id = w.board_id
  AND ssb.series_id <> w.series_id;

-- New rows go after the series' last position, in board id order.
INSERT INTO kaizen.sprint_series_boards (id, series_id, board_id, position)
SELECT
    gen_random_uuid(),
    w.series_id,
    w.board_id,
    (COALESCE(last_position.value, -1)
        + ROW_NUMBER() OVER (PARTITION BY w.series_id ORDER BY w.board_id))::INTEGER
FROM sprint_series_restore_wants w
JOIN sprint_series_restore r ON r.series_id = w.series_id
LEFT JOIN LATERAL (
    SELECT MAX(ssb.position) AS value
    FROM kaizen.sprint_series_boards ssb
    WHERE ssb.series_id = w.series_id
) last_position ON TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM kaizen.sprint_series_boards ssb
    WHERE ssb.series_id = w.series_id AND ssb.board_id = w.board_id
);

-- =====================================================
-- 4. Switch the restored series on again
-- =====================================================

-- The job finds a series with an ACTIVE sprint by its end date. A series that
-- creates its next sprint days before the end also needs next_creation_at, which
-- is set as the application computes it: the running sprint's end minus the days
-- before, when that falls after its start and no next sprint is planned yet.
UPDATE kaizen.sprint_series s
SET enabled = TRUE,
    last_run_status = NULL,
    last_run_message = NULL,
    next_creation_at = (
        SELECT sp.end_date - (s.create_days_before * INTERVAL '1 day')
        FROM kaizen.sprints sp
        WHERE sp.series_id = s.id
          AND sp.deleted = FALSE
          AND sp.status = 'ACTIVE'
          AND s.creation_mode = 'DAYS_BEFORE'
          AND s.create_days_before IS NOT NULL
          AND sp.end_date - (s.create_days_before * INTERVAL '1 day') > sp.start_date
          AND NOT EXISTS (
              SELECT 1 FROM kaizen.sprints planned
              WHERE planned.series_id = s.id
                AND planned.deleted = FALSE
                AND planned.status = 'PLANNED'
          )
        LIMIT 1
    ),
    updated_at = CURRENT_TIMESTAMP
FROM sprint_series_restore r
WHERE s.id = r.series_id;
