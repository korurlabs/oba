-- Oba Database Schema - Otak: Add Contact Source Url
-- V1_2_108__Otak_Add_Contact_Source_Url.sql
--
-- A contact records the page it was found on, the page a person is told about at
-- first contact. The column is optional: contacts created by hand or before this
-- migration have no source, so existing rows keep null and nothing is backfilled.
--
-- The length 500 matches otak.contacts.linkedin_url, the other address column.
-- The http or https format is validated by the API, as for linkedin_url, so the
-- table has no check constraint.
--
-- Indexes: none. The column is displayed, never filtered or sorted on.
--
-- Delete rules: none change. The column is not a key, and contacts_relation_id_organization_id_fk
-- keeps cascading from otak.relations.

-- =====================================================
-- 1. Alter tables
-- =====================================================

ALTER TABLE otak.contacts ADD COLUMN source_url VARCHAR(500);
