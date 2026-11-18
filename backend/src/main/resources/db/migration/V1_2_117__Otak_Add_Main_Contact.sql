-- Oba Database Schema - Otak: Add Main Contact
-- V1_2_117__Otak_Add_Main_Contact.sql
--
-- Marks one contact of a relation as its main contact, the person shown next
-- to the relation in the lists. A relation with contacts has exactly one main
-- contact; the application keeps that rule, the database refuses a second one.
--
-- Backfill: every relation that already has contacts gets its oldest contact
-- (created_at, then id as tie breaker) as main contact, so no relation with
-- contacts is left without one.
--
-- Delete rules: none change. The column is not a key, and
-- contacts_relation_id_organization_id_fk still deletes the contacts with
-- their relation.
--
-- Indexes:
--   contacts_relation_id_main_contact_uk is a partial unique index on
--     relation_id where main_contact is true. It refuses a second main contact
--     on one relation, and it serves the lookup of the main contact of a
--     relation and of a page of relations. Contacts that are not main are not
--     indexed, so the index stays small.

-- =====================================================
-- 1. Alter contacts
-- =====================================================

ALTER TABLE otak.contacts
    ADD COLUMN main_contact BOOLEAN NOT NULL DEFAULT false;

-- =====================================================
-- 2. Backfill the oldest contact of each relation
-- =====================================================

UPDATE otak.contacts
SET main_contact = true
WHERE id IN (
    SELECT DISTINCT ON (relation_id) id
    FROM otak.contacts
    ORDER BY relation_id, created_at, id
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX contacts_relation_id_main_contact_uk ON otak.contacts USING btree (relation_id) WHERE main_contact;
