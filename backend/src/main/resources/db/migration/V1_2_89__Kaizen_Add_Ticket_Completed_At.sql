-- Oba Database Schema - Kaizen: Add Ticket Completed At
-- V1_2_89__Kaizen_Add_Ticket_Completed_At.sql
--
-- kaizen.tickets.completed_at is the moment a ticket last reached DONE. The home
-- dashboard reads it to draw the sprint burndown and to score the "last 30 days"
-- leaderboard. The entity keeps it in step with status: set when a ticket becomes
-- DONE, cleared when it is reopened. NULL means the ticket is not DONE.

-- =====================================================
-- 1. Column
-- =====================================================

ALTER TABLE kaizen.tickets
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE NULL;

-- =====================================================
-- 2. Backfill
-- =====================================================

-- The true completion time was never stored, so the last edit is the closest
-- value. Tickets edited after reaching DONE therefore date later than they
-- finished. Soft-deleted rows are included so that a restored ticket is
-- consistent. updated_at is left alone so that no ticket looks edited. A rerun
-- updates nothing.
UPDATE kaizen.tickets
SET completed_at = updated_at
WHERE status = 'DONE'
  AND completed_at IS NULL;

-- =====================================================
-- 3. Index
-- =====================================================

-- Serves the date-window and burndown reads, which only look at finished
-- tickets. Open tickets (NULL) stay out of the index, which keeps it small.
CREATE INDEX IF NOT EXISTS tickets_completed_at_idx
    ON kaizen.tickets (completed_at) WHERE completed_at IS NOT NULL;
