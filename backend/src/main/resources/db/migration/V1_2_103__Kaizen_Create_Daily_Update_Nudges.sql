-- Oba Database Schema - Kaizen: Create Daily Update Nudges
-- V1_2_103__Kaizen_Create_Daily_Update_Nudges.sql
--
-- A member can nudge a colleague who has not posted a daily update. A nudge is
-- stored so that one recipient is nudged at most once per organization and
-- business day (Europe/Istanbul), whoever sends it. day is that business day as
-- a DATE.
--
-- Delete rules:
--   daily_update_nudges_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its nudges, as for boards.
--   daily_update_nudges_recipient_id_fk and daily_update_nudges_sender_id_fk
--     ON DELETE CASCADE: a nudge is a notification, not a record the user owns,
--     so it must not block deleting either user.
--
-- Indexes:
--   daily_update_nudges_organization_id_recipient_id_day_uk enforces one nudge
--     per recipient, organization and day; leading with organization_id it also
--     serves the organization foreign key cascade.
--   daily_update_nudges_recipient_id_idx and daily_update_nudges_sender_id_idx
--     serve the user foreign key cascades.

-- =====================================================
-- 1. Create tables
-- =====================================================

CREATE TABLE kaizen.daily_update_nudges (
    id UUID NOT NULL,
    day DATE NOT NULL,
    organization_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT daily_update_nudges_pk PRIMARY KEY (id),
    CONSTRAINT daily_update_nudges_organization_id_recipient_id_day_uk UNIQUE (organization_id, recipient_id, day),
    CONSTRAINT daily_update_nudges_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT daily_update_nudges_recipient_id_fk FOREIGN KEY (recipient_id) REFERENCES shrine.users (id) ON DELETE CASCADE,
    CONSTRAINT daily_update_nudges_sender_id_fk FOREIGN KEY (sender_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

CREATE INDEX daily_update_nudges_recipient_id_idx ON kaizen.daily_update_nudges (recipient_id);
CREATE INDEX daily_update_nudges_sender_id_idx ON kaizen.daily_update_nudges (sender_id);
