-- Oba Database Schema - Otak: Add Contact Email Type
-- V1_2_116__Otak_Add_Contact_Email_Type.sql
--
-- A contact can record what kind of address its e-mail is: COMPANY (the
-- person's own address at their company), SHARED (a team or company inbox that
-- others also read, such as info@) or PERSONAL (the person's private address).
-- The type is optional and no existing contact is classified, so the column is
-- nullable and there is no backfill: existing rows stay NULL.
--
-- The enum type is created in the public schema with an otak_ prefix, as in
-- V1_2_110 and V1_2_114. It is separate from otak_email_type_enum, which is
-- the kind of mailbox a mail sender sends from. The value order matches the
-- Java enum ContactEmailType.
--
-- Delete rules: none added. The column belongs to the contact row, so it goes
-- with the row when the relation cascades.
--
-- Indexes: none added. The column is never filtered on.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_contact_email_type_enum AS ENUM ('COMPANY', 'SHARED', 'PERSONAL');

-- =====================================================
-- 2. Add the type column
-- =====================================================

ALTER TABLE otak.contacts ADD COLUMN email_type otak_contact_email_type_enum;
