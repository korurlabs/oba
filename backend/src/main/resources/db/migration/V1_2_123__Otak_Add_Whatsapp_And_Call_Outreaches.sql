-- Oba Database Schema - Otak: Add WhatsApp And Call Outreaches
-- V1_2_123__Otak_Add_Whatsapp_And_Call_Outreaches.sql
--
-- An outreach is no longer always a mail: it can also be a WhatsApp message or
-- a call that someone records as done. Those two carry no mail, so
-- outreaches.mail_id (V1_2_122) becomes nullable, and two checks tie the mail
-- and the mail sender to the MAIL channel.
--
-- Existing rows are all MAIL with a mail and keep satisfying both checks. The
-- two checks only name the existing value MAIL, so they run in the same
-- transaction as ADD VALUE.
--
-- Constraints:
--   outreaches_mail_id_ck: a MAIL outreach has a mail, a WHATSAPP or CALL
--     outreach has none.
--   outreaches_mail_sender_id_ck: only a MAIL outreach has a mail sender.
--   outreaches_sent_ck (V1_2_119) is unchanged: a done WHATSAPP or CALL has
--     sent_to (the phone) and sent_at.
--
-- Delete rules and indexes: none change. outreaches_mail_id_uk stays unique and
-- ignores nulls, so any number of WHATSAPP and CALL outreaches may have no mail.

-- =====================================================
-- 1. Add enum values
-- =====================================================

ALTER TYPE otak_outreach_type_enum ADD VALUE IF NOT EXISTS 'WHATSAPP';
ALTER TYPE otak_outreach_type_enum ADD VALUE IF NOT EXISTS 'CALL';

-- =====================================================
-- 2. Make the mail optional
-- =====================================================

ALTER TABLE otak.outreaches ALTER COLUMN mail_id DROP NOT NULL;

-- =====================================================
-- 3. Create constraints
-- =====================================================

ALTER TABLE otak.outreaches ADD CONSTRAINT outreaches_mail_id_ck
    CHECK ((type = 'MAIL'::otak_outreach_type_enum) = (mail_id IS NOT NULL));
ALTER TABLE otak.outreaches ADD CONSTRAINT outreaches_mail_sender_id_ck
    CHECK (type = 'MAIL'::otak_outreach_type_enum OR mail_sender_id IS NULL);
