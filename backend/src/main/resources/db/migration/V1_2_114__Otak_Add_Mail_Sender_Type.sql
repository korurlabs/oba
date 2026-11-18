-- Oba Database Schema - Otak: Add Mail Sender Type
-- V1_2_114__Otak_Add_Mail_Sender_Type.sql
--
-- A mail sender is now either a PERSONAL mailbox (a user mailbox, found in the
-- tenant by its address) or a SHARED mailbox (found by its mail property). The
-- type is chosen when the sender is added. Every sender stored so far was
-- checked as a user mailbox, so existing rows become PERSONAL. The default
-- that backfills them is dropped again, so every new row must state its type.
--
-- The enum type is created in the public schema with an otak_ prefix, as in
-- V1_2_110.
--
-- Delete rules: none added.
--
-- Indexes: none added. The column is never filtered on, and the unique rule on
-- the address per organization stays independent of the type.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_email_type_enum AS ENUM ('PERSONAL', 'SHARED');

-- =====================================================
-- 2. Add the type column
-- =====================================================

ALTER TABLE otak.mail_senders ADD COLUMN type otak_email_type_enum NOT NULL DEFAULT 'PERSONAL';

ALTER TABLE otak.mail_senders ALTER COLUMN type DROP DEFAULT;
