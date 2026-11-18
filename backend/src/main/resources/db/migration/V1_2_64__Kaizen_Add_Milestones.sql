-- Database Schema - Kaizen Add Milestones
-- V1_2_64__Kaizen_Add_Milestones.sql
--
-- Milestones are dated targets owned by a board. Tickets attach to one milestone
-- through tickets.milestone_id. Progress and health are derived at read time and
-- are not stored.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE kaizen_milestone_status_enum AS ENUM ('OPEN', 'CLOSED');

-- =====================================================
-- 2. Create tables
-- =====================================================

-- Deleting a board removes its milestones with it (CASCADE), as for projects.
-- deleted_with_board marks the milestones a board soft-delete removed, so that
-- a board restore brings back only those and not milestones deleted one by one
-- before.
CREATE TABLE kaizen.milestones (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NULL,
    start_date DATE NULL,
    target_date DATE NOT NULL,
    status kaizen_milestone_status_enum NOT NULL DEFAULT 'OPEN'::kaizen_milestone_status_enum,
    board_id UUID NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_with_board BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT milestones_pk PRIMARY KEY (id),
    CONSTRAINT milestones_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE,
    CONSTRAINT milestones_start_date_ck CHECK (start_date IS NULL OR start_date <= target_date)
);

-- A deleted milestone keeps its row, so deleting a milestone must not leave
-- tickets pointing at it: the service clears milestone_id, and SET NULL covers
-- a hard delete.
ALTER TABLE kaizen.tickets ADD COLUMN milestone_id UUID NULL;
ALTER TABLE kaizen.tickets
    ADD CONSTRAINT tickets_milestone_id_fk FOREIGN KEY (milestone_id) REFERENCES kaizen.milestones (id) ON DELETE SET NULL;

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- Lists and roadmap lanes load milestones per board.
CREATE INDEX milestones_board_id_idx ON kaizen.milestones (board_id);

-- Roadmap window filter and ordering by target date.
CREATE INDEX milestones_target_date_idx ON kaizen.milestones (target_date);

-- Foreign key lookup: ticket counts per milestone, the ticket filter and the SET NULL cascade.
CREATE INDEX tickets_milestone_id_idx ON kaizen.tickets (milestone_id);
