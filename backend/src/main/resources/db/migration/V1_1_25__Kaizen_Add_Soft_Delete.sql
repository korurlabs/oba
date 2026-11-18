-- Village Database Schema - Kaizen: Add Soft Delete
-- V1_1_25__Kaizen_Add_Soft_Delete.sql
--
-- Enables soft delete for all 13 Kaizen entities:
--   - Adds deleted BOOLEAN NOT NULL DEFAULT FALSE to 11 existing tables
--   - Converts 2 join tables (board_sprints, ticket_labels) to entity-backed
--     tables with UUID PK, deleted, and timestamps
--   - Creates partial indexes (WHERE deleted = FALSE) for all 13 tables
--   - Converts unique constraints to partial unique indexes so that soft-deleted
--     records do not block re-adds of the same logical entity
--
-- Constraint name reference (verified from prior migrations):
--   tickets_key_uk           — V1_0_0
--   uq_boards_prefix         — V1_1_23
--   board_members_board_id_user_id_uk     — V1_0_0
--   ticket_assignees_ticket_id_user_id_uk — V1_0_0
--   ticket_reviewers_ticket_id_user_id_uk — V1_0_0
--   board_member_specialties_board_member_id_specialty_uk — V1_0_1
--   labels_board_id_name_uk  — V1_0_0
--   board_sprints_pk         — V1_1_19 (composite PK replaced below)
--   ticket_labels_pk         — V1_0_0  (composite PK replaced below)
--
-- Note: sprints had sprints_board_id_number_uk, but that constraint and the
--   board_id column were both dropped in V1_1_19 (ManyToMany refactor).
--   sprints therefore has no unique constraint to convert.

-- =====================================================
-- 1. Add deleted column to 11 existing tables
-- =====================================================

ALTER TABLE kaizen.tickets                ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.boards                 ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.sprints                ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.comments               ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.ticket_assignees       ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.ticket_reviewers       ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.board_members          ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.board_member_specialties ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.projects               ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.backlogs               ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.labels                 ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- =====================================================
-- 2. Convert join tables to entity-backed tables
--    Add id, deleted, created_at, updated_at
-- =====================================================

-- board_sprints
ALTER TABLE kaizen.board_sprints ADD COLUMN id         UUID                     NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE kaizen.board_sprints ADD COLUMN deleted     BOOLEAN                  NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.board_sprints ADD COLUMN created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE kaizen.board_sprints ADD COLUMN updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- ticket_labels
ALTER TABLE kaizen.ticket_labels ADD COLUMN id         UUID                     NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE kaizen.ticket_labels ADD COLUMN deleted     BOOLEAN                  NOT NULL DEFAULT FALSE;
ALTER TABLE kaizen.ticket_labels ADD COLUMN created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE kaizen.ticket_labels ADD COLUMN updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- =====================================================
-- 3. Replace composite PKs with UUID-based PKs on join tables
--    Must drop old PK before adding new one.
-- =====================================================

-- board_sprints: composite PK (board_id, sprint_id) → UUID PK
ALTER TABLE kaizen.board_sprints DROP CONSTRAINT board_sprints_pk;
ALTER TABLE kaizen.board_sprints ADD CONSTRAINT board_sprints_pk PRIMARY KEY (id);

-- ticket_labels: composite PK (ticket_id, label_id) → UUID PK
ALTER TABLE kaizen.ticket_labels DROP CONSTRAINT ticket_labels_pk;
ALTER TABLE kaizen.ticket_labels ADD CONSTRAINT ticket_labels_pk PRIMARY KEY (id);

-- =====================================================
-- 4. Partial indexes for active-record queries (all 13 tables)
-- =====================================================

CREATE INDEX tickets_deleted_idx                    ON kaizen.tickets                    (deleted) WHERE deleted = FALSE;
CREATE INDEX boards_deleted_idx                     ON kaizen.boards                     (deleted) WHERE deleted = FALSE;
CREATE INDEX sprints_deleted_idx                    ON kaizen.sprints                    (deleted) WHERE deleted = FALSE;
CREATE INDEX comments_deleted_idx                   ON kaizen.comments                   (deleted) WHERE deleted = FALSE;
CREATE INDEX ticket_assignees_deleted_idx           ON kaizen.ticket_assignees           (deleted) WHERE deleted = FALSE;
CREATE INDEX ticket_reviewers_deleted_idx           ON kaizen.ticket_reviewers           (deleted) WHERE deleted = FALSE;
CREATE INDEX board_members_deleted_idx              ON kaizen.board_members              (deleted) WHERE deleted = FALSE;
CREATE INDEX board_member_specialties_deleted_idx   ON kaizen.board_member_specialties   (deleted) WHERE deleted = FALSE;
CREATE INDEX projects_deleted_idx                   ON kaizen.projects                   (deleted) WHERE deleted = FALSE;
CREATE INDEX backlogs_deleted_idx                   ON kaizen.backlogs                   (deleted) WHERE deleted = FALSE;
CREATE INDEX labels_deleted_idx                     ON kaizen.labels                     (deleted) WHERE deleted = FALSE;
CREATE INDEX board_sprints_deleted_idx              ON kaizen.board_sprints              (deleted) WHERE deleted = FALSE;
CREATE INDEX ticket_labels_deleted_idx              ON kaizen.ticket_labels              (deleted) WHERE deleted = FALSE;

-- =====================================================
-- 5. Convert unique constraints to partial unique indexes
--    Allows the same logical record to be re-added after soft delete.
-- =====================================================

-- tickets: key must be unique among active tickets only
ALTER TABLE kaizen.tickets DROP CONSTRAINT tickets_key_uk;
CREATE UNIQUE INDEX tickets_key_uk ON kaizen.tickets (key) WHERE deleted = FALSE;

-- boards: prefix must be unique among active boards only
ALTER TABLE kaizen.boards DROP CONSTRAINT uq_boards_prefix;
CREATE UNIQUE INDEX boards_prefix_uk ON kaizen.boards (prefix) WHERE deleted = FALSE;

-- board_members: one membership per (board, user) among active records
ALTER TABLE kaizen.board_members DROP CONSTRAINT board_members_board_id_user_id_uk;
CREATE UNIQUE INDEX board_members_board_id_user_id_uk ON kaizen.board_members (board_id, user_id) WHERE deleted = FALSE;

-- ticket_assignees: one assignment per (ticket, user) among active records
ALTER TABLE kaizen.ticket_assignees DROP CONSTRAINT ticket_assignees_ticket_id_user_id_uk;
CREATE UNIQUE INDEX ticket_assignees_ticket_id_user_id_uk ON kaizen.ticket_assignees (ticket_id, user_id) WHERE deleted = FALSE;

-- ticket_reviewers: one reviewer assignment per (ticket, user) among active records
ALTER TABLE kaizen.ticket_reviewers DROP CONSTRAINT ticket_reviewers_ticket_id_user_id_uk;
CREATE UNIQUE INDEX ticket_reviewers_ticket_id_user_id_uk ON kaizen.ticket_reviewers (ticket_id, user_id) WHERE deleted = FALSE;

-- board_member_specialties: one specialty per (member, specialty value) among active records
ALTER TABLE kaizen.board_member_specialties DROP CONSTRAINT board_member_specialties_board_member_id_specialty_uk;
CREATE UNIQUE INDEX board_member_specialties_board_member_id_specialty_uk ON kaizen.board_member_specialties (board_member_id, specialty) WHERE deleted = FALSE;

-- labels: name must be unique within a board among active labels
ALTER TABLE kaizen.labels DROP CONSTRAINT labels_board_id_name_uk;
CREATE UNIQUE INDEX labels_board_id_name_uk ON kaizen.labels (board_id, name) WHERE deleted = FALSE;

-- board_sprints: one link per (board, sprint) among active links
--   (no prior constraint existed — this is the first uniqueness enforcement)
CREATE UNIQUE INDEX board_sprints_board_id_sprint_id_uk ON kaizen.board_sprints (board_id, sprint_id) WHERE deleted = FALSE;

-- ticket_labels: one label link per (ticket, label) among active links
--   (no prior constraint existed — this is the first uniqueness enforcement)
CREATE UNIQUE INDEX ticket_labels_ticket_id_label_id_uk ON kaizen.ticket_labels (ticket_id, label_id) WHERE deleted = FALSE;
