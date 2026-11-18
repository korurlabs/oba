-- Oba Database Schema - Otak: Drop Relation Do Not Contact
-- V1_2_121__Otak_Drop_Relation_Do_Not_Contact.sql
--
-- Drops otak.relations.do_not_contact (V1_2_100). The flag was set by hand and
-- nothing read or enforced it, so it added a required field to every relation
-- request without protecting anything. Its values are discarded.
--
-- No index, constraint, view or function references the column, so dropping it
-- touches nothing else.

-- =====================================================
-- 1. Drop columns
-- =====================================================

ALTER TABLE otak.relations DROP COLUMN do_not_contact;
