-- Database Schema - Kaizen Add Chat
-- V1_2_76__Kaizen_Add_Chat.sql
--
-- The chat of an organization. An organization has channels; messages, threads
-- and read states belong to exactly one channel. Threads, thread members,
-- mentions, ticket references and reactions hang off messages. Daily updates
-- also appear in the channel whose purpose is DAILY_UPDATES, as messages that
-- carry their daily update, so daily_updates gains the organization it was
-- posted to.

-- =====================================================
-- 1. Create enums
-- =====================================================

-- type decides how a channel behaves and is fixed at creation. purpose names an
-- automatic target for posts and is unique per organization (except GENERAL).
CREATE TYPE kaizen_chat_channel_type_enum AS ENUM ('TEXT');
CREATE TYPE kaizen_chat_channel_purpose_enum AS ENUM ('GENERAL', 'DAILY_UPDATES');
CREATE TYPE kaizen_chat_channel_visibility_enum AS ENUM ('PUBLIC', 'PRIVATE');
CREATE TYPE kaizen_chat_thread_status_enum AS ENUM ('ACTIVE', 'CLOSED', 'ARCHIVED');

-- =====================================================
-- 2. Create tables
-- =====================================================

-- Deleting an organization removes its channels (CASCADE), as for boards.
-- A channel is soft-deleted and keeps its content, so the row stays and deleted
-- marks it. created_by_id is NULL for channels created by a migration; deleting
-- the creator clears it (SET NULL) instead of blocking the user delete.
-- An automatic-post channel (purpose other than GENERAL) must be a public text
-- channel, because every member has to be able to read what is posted to it.
CREATE TABLE kaizen.chat_channels (
    id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    type kaizen_chat_channel_type_enum NOT NULL DEFAULT 'TEXT',
    purpose kaizen_chat_channel_purpose_enum NOT NULL DEFAULT 'GENERAL',
    visibility kaizen_chat_channel_visibility_enum NOT NULL,
    organization_id UUID NOT NULL,
    created_by_id UUID NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_channels_pk PRIMARY KEY (id),
    CONSTRAINT chat_channels_purpose_ck CHECK (purpose = 'GENERAL' OR (type = 'TEXT' AND visibility = 'PUBLIC')),
    CONSTRAINT chat_channels_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT chat_channels_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users (id) ON DELETE SET NULL
);

-- Members of a private channel. Public channels have no rows, because every
-- member of the organization can read them. Members go with the channel and
-- with the user (CASCADE). created_at is the join time.
CREATE TABLE kaizen.chat_channel_members (
    id UUID NOT NULL,
    channel_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_channel_members_pk PRIMARY KEY (id),
    CONSTRAINT chat_channel_members_channel_id_user_id_uk UNIQUE (channel_id, user_id),
    CONSTRAINT chat_channel_members_channel_id_fk FOREIGN KEY (channel_id) REFERENCES kaizen.chat_channels (id) ON DELETE CASCADE,
    CONSTRAINT chat_channel_members_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- Deleting an organization removes its threads (CASCADE), as for boards. A user
-- who created a thread cannot be hard-deleted (no cascade): the thread is the
-- user's record. The same holds for the user who last changed the status.
-- root_message_id is a plain column during creation because chat_messages does
-- not exist yet; its foreign key is added below.
-- ACTIVE threads accept replies, CLOSED threads are read-only, ARCHIVED threads
-- are put away manually. The archive that follows from inactivity is not stored:
-- it is computed from last_message_at and auto_archive_minutes when the thread
-- is read. status_changed_at and status_changed_by_id stay NULL until the status
-- is first changed.
CREATE TABLE kaizen.chat_threads (
    id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    root_message_id UUID NOT NULL,
    last_message_at TIMESTAMP WITH TIME ZONE NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    auto_archive_minutes INTEGER NOT NULL DEFAULT 10080,
    status kaizen_chat_thread_status_enum NOT NULL DEFAULT 'ACTIVE',
    status_changed_at TIMESTAMP WITH TIME ZONE NULL,
    channel_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    created_by_id UUID NOT NULL,
    status_changed_by_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_threads_pk PRIMARY KEY (id),
    CONSTRAINT chat_threads_root_message_id_uk UNIQUE (root_message_id),
    CONSTRAINT chat_threads_auto_archive_minutes_ck CHECK (auto_archive_minutes IN (60, 1440, 4320, 10080)),
    CONSTRAINT chat_threads_channel_id_fk FOREIGN KEY (channel_id) REFERENCES kaizen.chat_channels (id) ON DELETE CASCADE,
    CONSTRAINT chat_threads_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT chat_threads_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users (id),
    CONSTRAINT chat_threads_status_changed_by_id_fk FOREIGN KEY (status_changed_by_id) REFERENCES shrine.users (id)
);

-- Soft-deleted by the author or a manager, so the row stays and deleted marks
-- it. A thread's messages go with the thread (CASCADE on thread_id) and a
-- channel's messages go with the channel (CASCADE on channel_id). A reply
-- target that disappears clears reply_to_message_id (SET NULL). A message that
-- carries a daily update always lives in the channel itself, never in a thread.
-- client_nonce is the key the sender's client generated for the send, so a
-- send repeated after a lost response returns the stored message instead of a
-- second copy; NULL for rows written before the client sent one.
CREATE TABLE kaizen.chat_messages (
    id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL DEFAULT '',
    mentions_everyone BOOLEAN NOT NULL DEFAULT FALSE,
    mentions_here BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at TIMESTAMP WITH TIME ZONE NULL,
    pinned_at TIMESTAMP WITH TIME ZONE NULL,
    channel_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    thread_id UUID NULL,
    author_id UUID NOT NULL,
    daily_update_id UUID NULL,
    reply_to_message_id UUID NULL,
    pinned_by_id UUID NULL,
    client_nonce VARCHAR(64) NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_messages_pk PRIMARY KEY (id),
    CONSTRAINT chat_messages_daily_update_id_uk UNIQUE (daily_update_id),
    CONSTRAINT chat_messages_daily_update_thread_ck CHECK (daily_update_id IS NULL OR thread_id IS NULL),
    CONSTRAINT chat_messages_channel_id_fk FOREIGN KEY (channel_id) REFERENCES kaizen.chat_channels (id) ON DELETE CASCADE,
    CONSTRAINT chat_messages_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT chat_messages_thread_id_fk FOREIGN KEY (thread_id) REFERENCES kaizen.chat_threads (id) ON DELETE CASCADE,
    CONSTRAINT chat_messages_author_id_fk FOREIGN KEY (author_id) REFERENCES shrine.users (id),
    CONSTRAINT chat_messages_daily_update_id_fk FOREIGN KEY (daily_update_id) REFERENCES kaizen.daily_updates (id),
    CONSTRAINT chat_messages_reply_to_message_id_fk FOREIGN KEY (reply_to_message_id) REFERENCES kaizen.chat_messages (id) ON DELETE SET NULL,
    CONSTRAINT chat_messages_pinned_by_id_fk FOREIGN KEY (pinned_by_id) REFERENCES shrine.users (id)
);

-- Messages are only soft-deleted, so this foreign key never fires on a message
-- delete; it only guarantees the root exists when the thread is created.
ALTER TABLE kaizen.chat_threads
    ADD CONSTRAINT chat_threads_root_message_id_fk FOREIGN KEY (root_message_id) REFERENCES kaizen.chat_messages (id);

-- Members of a thread go with the thread (CASCADE). created_at is the join time.
CREATE TABLE kaizen.chat_thread_members (
    id UUID NOT NULL,
    thread_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_thread_members_pk PRIMARY KEY (id),
    CONSTRAINT chat_thread_members_thread_id_user_id_uk UNIQUE (thread_id, user_id),
    CONSTRAINT chat_thread_members_thread_id_fk FOREIGN KEY (thread_id) REFERENCES kaizen.chat_threads (id) ON DELETE CASCADE,
    CONSTRAINT chat_thread_members_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- Mentions are derived from the message text and go with the message (CASCADE).
-- via_here marks users reached only through @here.
CREATE TABLE kaizen.chat_message_mentions (
    id UUID NOT NULL,
    via_here BOOLEAN NOT NULL DEFAULT FALSE,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_message_mentions_pk PRIMARY KEY (id),
    CONSTRAINT chat_message_mentions_message_id_user_id_uk UNIQUE (message_id, user_id),
    CONSTRAINT chat_message_mentions_message_id_fk FOREIGN KEY (message_id) REFERENCES kaizen.chat_messages (id) ON DELETE CASCADE,
    CONSTRAINT chat_message_mentions_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- Ticket references are snapshots as daily_update_tickets: ticket_id is cleared
-- when the ticket is hard-deleted (SET NULL) so the reference survives as plain
-- text; a board hard delete removes the references (CASCADE on board_id).
CREATE TABLE kaizen.chat_message_ticket_refs (
    id UUID NOT NULL,
    ticket_key VARCHAR(50) NOT NULL,
    ticket_title VARCHAR(500) NOT NULL,
    position INTEGER NOT NULL,
    message_id UUID NOT NULL,
    ticket_id UUID NULL,
    board_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_message_ticket_refs_pk PRIMARY KEY (id),
    CONSTRAINT chat_message_ticket_refs_message_id_ticket_id_uk UNIQUE (message_id, ticket_id),
    CONSTRAINT chat_message_ticket_refs_message_id_fk FOREIGN KEY (message_id) REFERENCES kaizen.chat_messages (id) ON DELETE CASCADE,
    CONSTRAINT chat_message_ticket_refs_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE SET NULL,
    CONSTRAINT chat_message_ticket_refs_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE
);

-- One reaction per user, message and emoji. Reactions go with the message
-- (CASCADE) and with the user.
CREATE TABLE kaizen.chat_reactions (
    id UUID NOT NULL,
    emoji VARCHAR(64) NOT NULL,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_reactions_pk PRIMARY KEY (id),
    CONSTRAINT chat_reactions_message_id_user_id_emoji_uk UNIQUE (message_id, user_id, emoji),
    CONSTRAINT chat_reactions_message_id_fk FOREIGN KEY (message_id) REFERENCES kaizen.chat_messages (id) ON DELETE CASCADE,
    CONSTRAINT chat_reactions_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- The read position of a user: one row per (user, channel) for the channel
-- itself (thread_id NULL) and one per (user, thread), enforced by the partial
-- unique indexes below. last_read_message_id is cleared when that message row
-- goes (SET NULL); last_read_at keeps the position. A thread read state also
-- carries the channel of its thread.
CREATE TABLE kaizen.chat_read_states (
    id UUID NOT NULL,
    last_read_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_read_message_id UUID NULL,
    user_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    thread_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_read_states_pk PRIMARY KEY (id),
    CONSTRAINT chat_read_states_last_read_message_id_fk FOREIGN KEY (last_read_message_id) REFERENCES kaizen.chat_messages (id) ON DELETE SET NULL,
    CONSTRAINT chat_read_states_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE,
    CONSTRAINT chat_read_states_channel_id_fk FOREIGN KEY (channel_id) REFERENCES kaizen.chat_channels (id) ON DELETE CASCADE,
    CONSTRAINT chat_read_states_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT chat_read_states_thread_id_fk FOREIGN KEY (thread_id) REFERENCES kaizen.chat_threads (id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Alter existing tables
-- =====================================================

-- The organization a daily update was posted to. NULL for authors without an
-- organization; they have no chat message. Deleting the organization clears it
-- (SET NULL) instead of dropping the author's updates.
ALTER TABLE kaizen.daily_updates
    ADD COLUMN organization_id UUID NULL;

ALTER TABLE kaizen.daily_updates
    ADD CONSTRAINT daily_updates_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE SET NULL;

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- At most one channel per automatic-post purpose in an organization. GENERAL is
-- excluded because an organization may have several general channels. Deleted
-- channels are excluded so the purpose can move to a new channel.
CREATE UNIQUE INDEX chat_channels_purpose_uk ON kaizen.chat_channels (organization_id, purpose) WHERE purpose <> 'GENERAL' AND deleted = FALSE;
-- Channel list of an organization in creation order (also serves the organization_id foreign key).
CREATE INDEX chat_channels_organization_id_created_at_idx ON kaizen.chat_channels (organization_id, created_at, id);
-- Foreign key lookup: channels of a creator, and the SET NULL on user delete.
CREATE INDEX chat_channels_created_by_id_idx ON kaizen.chat_channels (created_by_id);

-- Foreign key lookup: private channels a user belongs to (the channel side is covered by the unique constraint).
CREATE INDEX chat_channel_members_user_id_idx ON kaizen.chat_channel_members (user_id);

-- Thread list of a channel, most recently active first, and the same list filtered
-- by status (one list tab per status). Also serves the channel_id foreign key.
CREATE INDEX chat_threads_channel_id_last_message_at_idx ON kaizen.chat_threads (channel_id, last_message_at DESC, id DESC);
CREATE INDEX chat_threads_channel_id_status_last_message_at_idx ON kaizen.chat_threads (channel_id, status, last_message_at DESC, id DESC);
-- Foreign key lookups.
CREATE INDEX chat_threads_organization_id_idx ON kaizen.chat_threads (organization_id);
CREATE INDEX chat_threads_created_by_id_idx ON kaizen.chat_threads (created_by_id);
CREATE INDEX chat_threads_status_changed_by_id_idx ON kaizen.chat_threads (status_changed_by_id);

-- Message pages and unread counts of a channel, and the messages of an author
-- in a channel. Full, so it also serves the channel_id foreign key.
CREATE INDEX chat_messages_channel_id_created_at_idx ON kaizen.chat_messages (channel_id, created_at DESC, id DESC);
-- Thread pages and thread unread counts. Full, so it also serves the thread_id foreign key.
CREATE INDEX chat_messages_thread_id_created_at_idx ON kaizen.chat_messages (thread_id, created_at DESC, id DESC);
-- Pinned messages of a channel.
CREATE INDEX chat_messages_pinned_idx ON kaizen.chat_messages (channel_id, pinned_at DESC) WHERE pinned_at IS NOT NULL AND deleted = FALSE;
-- Search over all messages of an organization, including thread replies, newest first
-- (also serves the organization_id foreign key).
CREATE INDEX chat_messages_organization_id_created_at_idx ON kaizen.chat_messages (organization_id, created_at DESC, id DESC);
-- Foreign key lookups and the SET NULL on reply target delete.
CREATE INDEX chat_messages_author_id_idx ON kaizen.chat_messages (author_id);
CREATE INDEX chat_messages_reply_to_message_id_idx ON kaizen.chat_messages (reply_to_message_id);
CREATE INDEX chat_messages_pinned_by_id_idx ON kaizen.chat_messages (pinned_by_id);
-- One message per send of an author: a repeated send finds the stored row, and
-- of two concurrent sends with the same nonce only one is stored. Deleted rows
-- keep their nonce, so a late repeat cannot bring a deleted message back.
CREATE UNIQUE INDEX chat_messages_author_id_client_nonce_uk ON kaizen.chat_messages (author_id, client_nonce) WHERE client_nonce IS NOT NULL;

-- Foreign key lookup: threads a user belongs to (the thread side is covered by the unique constraint).
CREATE INDEX chat_thread_members_user_id_idx ON kaizen.chat_thread_members (user_id);

-- Foreign key lookup: messages that mention a user (the message side is covered by the unique constraint).
CREATE INDEX chat_message_mentions_user_id_idx ON kaizen.chat_message_mentions (user_id);

-- Foreign key lookups and the SET NULL on ticket delete and CASCADE on board delete
-- (the message side is covered by the unique constraint).
CREATE INDEX chat_message_ticket_refs_ticket_id_idx ON kaizen.chat_message_ticket_refs (ticket_id);
CREATE INDEX chat_message_ticket_refs_board_id_idx ON kaizen.chat_message_ticket_refs (board_id);

-- Foreign key lookup: reactions of a user (the message side is covered by the unique constraint).
CREATE INDEX chat_reactions_user_id_idx ON kaizen.chat_reactions (user_id);

-- One channel read state per user and channel, one thread read state per user and thread.
CREATE UNIQUE INDEX chat_read_states_channel_uk ON kaizen.chat_read_states (user_id, channel_id) WHERE thread_id IS NULL;
CREATE UNIQUE INDEX chat_read_states_thread_uk ON kaizen.chat_read_states (user_id, thread_id) WHERE thread_id IS NOT NULL;
-- Foreign key lookups.
CREATE INDEX chat_read_states_channel_id_idx ON kaizen.chat_read_states (channel_id);
CREATE INDEX chat_read_states_organization_id_idx ON kaizen.chat_read_states (organization_id);
CREATE INDEX chat_read_states_thread_id_idx ON kaizen.chat_read_states (thread_id);
CREATE INDEX chat_read_states_last_read_message_id_idx ON kaizen.chat_read_states (last_read_message_id);
-- Foreign key lookup for the CASCADE on user delete; the partial unique indexes cannot serve it.
CREATE INDEX chat_read_states_user_id_idx ON kaizen.chat_read_states (user_id);

-- Foreign key lookup: updates posted to an organization.
CREATE INDEX daily_updates_organization_id_idx ON kaizen.daily_updates (organization_id);
