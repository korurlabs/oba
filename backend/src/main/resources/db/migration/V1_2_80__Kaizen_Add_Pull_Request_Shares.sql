-- Database Schema - Kaizen Add Pull Request Shares
-- V1_2_80__Kaizen_Add_Pull_Request_Shares.sql
--
-- A GitHub pull request shared by a member in the pull requests chat channel,
-- with the reviewers asked to look at it and who approved. The chat entry
-- that shows a share is a chat message that carries the share, as it does for
-- a daily update.

-- =====================================================
-- 1. Create enums
-- =====================================================

-- Review state the author chose for the share; it is not read back from GitHub.
CREATE TYPE kaizen_pull_request_share_status_enum AS ENUM ('DRAFT', 'OPEN', 'IN_REVIEW', 'MERGED', 'DISCUSS');

-- =====================================================
-- 2. Create tables
-- =====================================================

-- Deleting an organization removes its shares (CASCADE), as for chat messages.
-- A user who shared a pull request cannot be hard-deleted (no cascade): the
-- share is the user's record, as for daily updates. A share is soft-deleted by
-- its author, so the row stays and deleted marks it. The link to GitHub is not
-- stored: it is built from repository_full_name and number. ticket_key is the
-- ticket key found in the branch or the title, if any, kept as plain text.
CREATE TABLE kaizen.pull_request_shares (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    author_id UUID NOT NULL,
    github_repository_id BIGINT NOT NULL,
    repository_full_name VARCHAR(200) NOT NULL,
    number INTEGER NOT NULL,
    title VARCHAR(500) NOT NULL,
    github_author_login VARCHAR(100) NOT NULL,
    head_ref VARCHAR(255) NOT NULL,
    base_ref VARCHAR(255) NOT NULL,
    ticket_key VARCHAR(50) NULL,
    status kaizen_pull_request_share_status_enum NOT NULL,
    note VARCHAR(500) NULL,
    edited_at TIMESTAMP WITH TIME ZONE NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pull_request_shares_pk PRIMARY KEY (id),
    CONSTRAINT pull_request_shares_number_ck CHECK (number > 0),
    CONSTRAINT pull_request_shares_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT pull_request_shares_author_id_fk FOREIGN KEY (author_id) REFERENCES shrine.users (id)
);

-- Reviewers of a share go with the share and with the user (CASCADE). position
-- keeps the order the author chose. approved_at is NULL until the reviewer
-- approves. A user is a reviewer of a share at most once.
CREATE TABLE kaizen.pull_request_share_reviewers (
    id UUID NOT NULL,
    share_id UUID NOT NULL,
    user_id UUID NOT NULL,
    position INTEGER NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pull_request_share_reviewers_pk PRIMARY KEY (id),
    CONSTRAINT pull_request_share_reviewers_share_id_user_id_uk UNIQUE (share_id, user_id),
    CONSTRAINT pull_request_share_reviewers_share_id_fk FOREIGN KEY (share_id) REFERENCES kaizen.pull_request_shares (id) ON DELETE CASCADE,
    CONSTRAINT pull_request_share_reviewers_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Alter existing tables
-- =====================================================

-- The share a chat message carries. A share has at most one message. Like
-- daily_update_id it has no cascade, because messages and shares are only
-- soft-deleted. A message that carries a share lives in the channel itself,
-- never in a thread, and never carries a daily update as well.
ALTER TABLE kaizen.chat_messages
    ADD COLUMN pull_request_share_id UUID NULL;

ALTER TABLE kaizen.chat_messages
    ADD CONSTRAINT chat_messages_pull_request_share_id_uk UNIQUE (pull_request_share_id),
    ADD CONSTRAINT chat_messages_pull_request_share_id_fk FOREIGN KEY (pull_request_share_id) REFERENCES kaizen.pull_request_shares (id),
    ADD CONSTRAINT chat_messages_pull_request_share_thread_ck CHECK (pull_request_share_id IS NULL OR thread_id IS NULL),
    ADD CONSTRAINT chat_messages_pull_request_share_daily_update_ck CHECK (pull_request_share_id IS NULL OR daily_update_id IS NULL);

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- Share list of an organization, newest first (also serves the organization_id foreign key).
CREATE INDEX pull_request_shares_organization_id_created_at_idx ON kaizen.pull_request_shares (organization_id, created_at DESC, id DESC);
-- Foreign key lookup: shares of an author (a user delete is refused while shares exist).
CREATE INDEX pull_request_shares_author_id_idx ON kaizen.pull_request_shares (author_id);

-- Foreign key lookup for the CASCADE on user delete, and the shares a user is asked to review
-- (the share side is covered by the unique constraint).
CREATE INDEX pull_request_share_reviewers_user_id_idx ON kaizen.pull_request_share_reviewers (user_id);
