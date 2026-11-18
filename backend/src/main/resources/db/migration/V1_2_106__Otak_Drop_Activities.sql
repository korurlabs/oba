-- Database Schema - Otak Drop Activities
-- V1_2_106__Otak_Drop_Activities.sql
--
-- Removes the timeline of activities per relation from the Otak app. A relation
-- keeps its free text in otak.relations.notes only. Existing timeline rows are
-- deleted, not copied into notes.
--
-- Dropping otak.activities also drops its primary key, its two foreign keys
-- (activities_relation_id_organization_id_fk to otak.relations and
-- activities_author_user_id_fk to shrine.users) and its two indexes
-- (activities_relation_id_occurred_on_created_at_idx and
-- activities_author_user_id_idx). No other table depends on activities, and
-- relations_id_organization_id_uk stays because the composite foreign key of
-- otak.contacts still uses it.

-- =====================================================
-- 1. Drop tables
-- =====================================================

DROP TABLE otak.activities;

-- =====================================================
-- 2. Drop enums
-- =====================================================

DROP TYPE otak_activity_type_enum;
