-- Database Schema - Kaizen Add Sprint Recurrences
-- V1_2_67__Kaizen_Add_Sprint_Recurrences.sql
--
-- A sprint recurrence holds the automatic-sprint setting of one sprint: whether
-- the next sprint is created on its own, when, and what happens to unfinished
-- tickets. No row means the switch is off. The job also records the outcome of
-- its last run and the PLANNED sprint it created ahead.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE kaizen_sprint_recurrence_ticket_action_enum AS ENUM ('MOVE_TO_NEXT_SPRINT', 'MOVE_TO_BACKLOG');

CREATE TYPE kaizen_sprint_recurrence_creation_mode_enum AS ENUM ('AT_END', 'DAYS_BEFORE');

CREATE TYPE kaizen_sprint_recurrence_run_status_enum AS ENUM ('OK', 'BLOCKED', 'FAILED');

-- =====================================================
-- 2. Create tables
-- =====================================================

-- The row goes with its sprint (CASCADE on sprint_id). A hard delete of the
-- sprint created ahead clears next_sprint_id (SET NULL) so the setting stays
-- and the job creates a new next sprint. The upper bound of create_days_before
-- against the sprint length is checked in the service, because the length is
-- derived from the dates on kaizen.sprints.
CREATE TABLE kaizen.sprint_recurrences (
    id UUID NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    unfinished_ticket_action kaizen_sprint_recurrence_ticket_action_enum NOT NULL DEFAULT 'MOVE_TO_NEXT_SPRINT',
    creation_mode kaizen_sprint_recurrence_creation_mode_enum NOT NULL DEFAULT 'AT_END',
    create_days_before INTEGER NULL,
    next_creation_at TIMESTAMP WITH TIME ZONE NULL,
    last_run_at TIMESTAMP WITH TIME ZONE NULL,
    last_run_status kaizen_sprint_recurrence_run_status_enum NULL,
    last_run_message VARCHAR(500) NULL,
    sprint_id UUID NOT NULL,
    next_sprint_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sprint_recurrences_pk PRIMARY KEY (id),
    CONSTRAINT sprint_recurrences_sprint_id_fk FOREIGN KEY (sprint_id) REFERENCES kaizen.sprints (id) ON DELETE CASCADE,
    CONSTRAINT sprint_recurrences_next_sprint_id_fk FOREIGN KEY (next_sprint_id) REFERENCES kaizen.sprints (id) ON DELETE SET NULL,
    CONSTRAINT sprint_recurrences_sprint_id_uk UNIQUE (sprint_id),
    CONSTRAINT sprint_recurrences_create_days_before_ck CHECK (create_days_before IS NULL OR create_days_before BETWEEN 1 AND 364)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- Foreign key lookup: the SET NULL on sprint delete. sprint_id needs no extra
-- index because the unique constraint already creates one.
CREATE INDEX sprint_recurrences_next_sprint_id_idx ON kaizen.sprint_recurrences (next_sprint_id);

-- The scheduled job reads only the enabled rows, which are few next to all rows.
CREATE INDEX sprint_recurrences_enabled_idx ON kaizen.sprint_recurrences (sprint_id) WHERE enabled;
