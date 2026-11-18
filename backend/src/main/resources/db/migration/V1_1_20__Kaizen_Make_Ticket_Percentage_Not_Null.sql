-- Village Database Schema - Kaizen: Make Ticket Percentage Not Null
-- V1_1_20__Kaizen_Make_Ticket_Percentage_Not_Null.sql
--
-- The percentage column on kaizen.tickets was originally nullable.
-- It now has a default value of 0 and must never be null.
--
--   1. Back-fill any existing NULL rows to 0.
--   2. Add DEFAULT 0 so new inserts without an explicit value are covered.
--   3. Add NOT NULL constraint.

-- =====================================================
-- 1. Back-fill existing NULLs
-- =====================================================

UPDATE kaizen.tickets
SET percentage = 0
WHERE percentage IS NULL;

-- =====================================================
-- 2. Set column default and NOT NULL constraint
-- =====================================================

ALTER TABLE kaizen.tickets
    ALTER COLUMN percentage SET DEFAULT 0,
    ALTER COLUMN percentage SET NOT NULL;
