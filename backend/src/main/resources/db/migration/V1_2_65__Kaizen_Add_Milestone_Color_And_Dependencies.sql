-- Database Schema - Kaizen Add Milestone Color And Dependencies
-- V1_2_65__Kaizen_Add_Milestone_Color_And_Dependencies.sql
--
-- A milestone carries its own bar colour, stored in the same format as
-- boards.color. A milestone can depend on another one ("B depends on A": B
-- should not start before A's target date); the conflict flag is derived at
-- read time and is not stored.

-- =====================================================
-- 1. Add the milestone colour
-- =====================================================

ALTER TABLE kaizen.milestones ADD COLUMN color VARCHAR(7) NULL;

-- Existing milestones keep the look they had: the colour of their board.
UPDATE kaizen.milestones m
SET color = b.color
FROM kaizen.boards b
WHERE b.id = m.board_id;

-- =====================================================
-- 2. Create tables
-- =====================================================

-- A dependency is a fact, not a record to restore, so rows are hard-deleted.
-- Deleting either milestone row removes the dependency (CASCADE); a soft-deleted
-- milestone keeps its row and the service excludes it from responses.
CREATE TABLE kaizen.milestone_dependencies (
    id UUID NOT NULL,
    milestone_id UUID NOT NULL,
    depends_on_milestone_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT milestone_dependencies_pk PRIMARY KEY (id),
    CONSTRAINT milestone_dependencies_milestone_id_fk FOREIGN KEY (milestone_id) REFERENCES kaizen.milestones (id) ON DELETE CASCADE,
    CONSTRAINT milestone_dependencies_depends_on_milestone_id_fk FOREIGN KEY (depends_on_milestone_id) REFERENCES kaizen.milestones (id) ON DELETE CASCADE,
    CONSTRAINT milestone_dependencies_milestone_id_depends_on_milestone_id_uk UNIQUE (milestone_id, depends_on_milestone_id),
    CONSTRAINT milestone_dependencies_not_self_ck CHECK (milestone_id <> depends_on_milestone_id)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- The unique constraint already indexes milestone_id (its leading column).
-- Foreign key lookup: the dependents of a milestone and the CASCADE delete.
CREATE INDEX milestone_dependencies_depends_on_milestone_id_idx ON kaizen.milestone_dependencies (depends_on_milestone_id);
