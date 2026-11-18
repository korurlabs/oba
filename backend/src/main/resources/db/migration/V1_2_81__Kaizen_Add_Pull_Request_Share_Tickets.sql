-- Database Schema - Kaizen Add Pull Request Share Tickets
-- V1_2_81__Kaizen_Add_Pull_Request_Share_Tickets.sql
--
-- The related tickets an author picks for a pull request share. Each row keeps
-- the board name, key and title as they were when the ticket was added, so the
-- card reads the same for every reader, as the tickets of a daily update do.

-- =====================================================
-- 1. Create tables
-- =====================================================

-- Related tickets go with the share (CASCADE). Tickets are only soft-deleted,
-- so a deleted ticket keeps its row; CASCADE on ticket_id and board_id fires
-- only on a hard delete, and a share has no ticketless row to keep. position
-- keeps the order the author chose. A ticket is related to a share at most once.
CREATE TABLE kaizen.pull_request_share_tickets (
    id UUID NOT NULL,
    share_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    board_id UUID NOT NULL,
    ticket_key VARCHAR(50) NOT NULL,
    ticket_title VARCHAR(500) NOT NULL,
    board_name VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pull_request_share_tickets_pk PRIMARY KEY (id),
    CONSTRAINT pull_request_share_tickets_share_id_ticket_id_uk UNIQUE (share_id, ticket_id),
    CONSTRAINT pull_request_share_tickets_share_id_fk FOREIGN KEY (share_id) REFERENCES kaizen.pull_request_shares (id) ON DELETE CASCADE,
    CONSTRAINT pull_request_share_tickets_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE CASCADE,
    CONSTRAINT pull_request_share_tickets_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Foreign key lookup for the CASCADE on ticket delete (the share side is
-- covered by the unique constraint).
CREATE INDEX pull_request_share_tickets_ticket_id_idx ON kaizen.pull_request_share_tickets (ticket_id);
-- Foreign key lookup for the CASCADE on board delete.
CREATE INDEX pull_request_share_tickets_board_id_idx ON kaizen.pull_request_share_tickets (board_id);
