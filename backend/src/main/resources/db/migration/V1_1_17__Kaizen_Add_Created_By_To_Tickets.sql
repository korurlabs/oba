-- Village Database Schema - Kaizen: Add created_by_id to tickets
-- V1_1_17__Kaizen_Add_Created_By_To_Tickets.sql
--
-- Adds the created_by_id column to kaizen.tickets to record which user
-- created each ticket. Existing tickets are backfilled with the board owner.
--
-- Dependencies: V1_0_0__Initial_Schema.sql (kaizen.tickets, kaizen.board_members, shrine.users)

-- =====================================================
-- 1. Add created_by_id column (nullable for backfill)
-- =====================================================

ALTER TABLE kaizen.tickets
    ADD COLUMN created_by_id UUID NULL;

-- =====================================================
-- 2. Backfill existing tickets with the board owner
-- =====================================================

UPDATE kaizen.tickets t
SET created_by_id = (
    SELECT bm.user_id
    FROM kaizen.board_members bm
    WHERE bm.board_id = t.board_id
      AND bm.role = 'OWNER'
    LIMIT 1
)
WHERE t.created_by_id IS NULL;

-- =====================================================
-- 3. Enforce NOT NULL now that all rows are populated
-- =====================================================

ALTER TABLE kaizen.tickets
    ALTER COLUMN created_by_id SET NOT NULL;

-- =====================================================
-- 4. Add FK constraint
-- =====================================================

ALTER TABLE kaizen.tickets
    ADD CONSTRAINT tickets_created_by_id_fk
        FOREIGN KEY (created_by_id)
        REFERENCES shrine.users (id)
        ON DELETE RESTRICT;

-- =====================================================
-- 5. Add index on created_by_id
-- =====================================================

CREATE INDEX tickets_created_by_id_idx ON kaizen.tickets (created_by_id);
