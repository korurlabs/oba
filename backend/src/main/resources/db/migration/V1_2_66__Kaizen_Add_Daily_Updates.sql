-- Database Schema - Kaizen Add Daily Updates
-- V1_2_66__Kaizen_Add_Daily_Updates.sql
--
-- A daily update is a status post written by one user: free-text focus and
-- questions plus one line per ticket. The lines are snapshots of the ticket at
-- posting time, so they keep their text when the ticket is renamed or deleted.

-- =====================================================
-- 1. Create tables
-- =====================================================

-- Soft-deleted by the author, so the row stays and deleted marks it.
-- Deleting a user is blocked while the user has updates (no cascade): an
-- update is the author's record and is not silently dropped.
CREATE TABLE kaizen.daily_updates (
    id UUID NOT NULL,
    focus VARCHAR(2000) NULL,
    questions VARCHAR(2000) NULL,
    edited_at TIMESTAMP WITH TIME ZONE NULL,
    author_id UUID NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT daily_updates_pk PRIMARY KEY (id),
    CONSTRAINT daily_updates_author_id_fk FOREIGN KEY (author_id) REFERENCES shrine.users (id)
);

-- Lines belong to their update and go with it (CASCADE). ticket_id is cleared
-- when the ticket is hard-deleted (SET NULL) so the line survives as plain text
-- with its copied key and title. A board hard delete removes the lines of its
-- tickets (CASCADE on board_id) because they can no longer be opened or shown.
-- The unique constraint allows a ticket once per update; rows with a NULL
-- ticket_id never collide.
CREATE TABLE kaizen.daily_update_tickets (
    id UUID NOT NULL,
    daily_update_id UUID NOT NULL,
    ticket_id UUID NULL,
    board_id UUID NOT NULL,
    ticket_key VARCHAR(50) NOT NULL,
    ticket_title VARCHAR(500) NOT NULL,
    board_name VARCHAR(255) NOT NULL,
    percentage INTEGER NOT NULL,
    note VARCHAR(500) NULL,
    progress_synced BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT daily_update_tickets_pk PRIMARY KEY (id),
    CONSTRAINT daily_update_tickets_daily_update_id_fk FOREIGN KEY (daily_update_id) REFERENCES kaizen.daily_updates (id) ON DELETE CASCADE,
    CONSTRAINT daily_update_tickets_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE SET NULL,
    CONSTRAINT daily_update_tickets_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE,
    CONSTRAINT daily_update_tickets_percentage_ck CHECK (percentage BETWEEN 0 AND 100),
    CONSTRAINT daily_update_tickets_daily_update_id_ticket_id_uk UNIQUE (daily_update_id, ticket_id)
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Foreign key lookup: "my updates" and the author join of the feed.
CREATE INDEX daily_updates_author_id_idx ON kaizen.daily_updates (author_id);

-- The feed is ordered by creation time, newest first, and grouped by day.
CREATE INDEX daily_updates_created_at_idx ON kaizen.daily_updates (created_at);

-- Foreign key lookup: loading the lines of an update and the CASCADE delete.
CREATE INDEX daily_update_tickets_daily_update_id_idx ON kaizen.daily_update_tickets (daily_update_id);

-- Foreign key lookup: the SET NULL on ticket delete and the lines of one ticket.
CREATE INDEX daily_update_tickets_ticket_id_idx ON kaizen.daily_update_tickets (ticket_id);

-- Foreign key lookup: the board visibility check on the feed and the CASCADE delete.
CREATE INDEX daily_update_tickets_board_id_idx ON kaizen.daily_update_tickets (board_id);
