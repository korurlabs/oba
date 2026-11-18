-- Oba Database Schema - Kaizen: Drop Sprint Recurrences And Number
-- V1_2_91__Kaizen_Drop_Sprint_Recurrences_And_Number.sql
--
-- The per-sprint recurrence rows were converted into kaizen.sprint_series by
-- V1_2_90, and the series round replaces the per-sprint number. Both old
-- storages go. The three kaizen_sprint_recurrence_*_enum types stay: they type
-- columns of kaizen.sprint_series.

-- =====================================================
-- 1. Drop the old recurrence table
-- =====================================================

DROP TABLE kaizen.sprint_recurrences;

-- =====================================================
-- 2. Drop the sprint number
-- =====================================================

ALTER TABLE kaizen.sprints DROP COLUMN number;
