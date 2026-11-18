-- Village Database Schema - Kaizen: Update Ticket Enums And Add Incident Fields
-- V1_1_16__Kaizen_Update_Ticket_Enums_And_Add_Incident_Fields.sql
--
-- This migration makes three related changes to the kaizen ticket domain:
--
--   1. Replaces kaizen_ticket_type_enum ('ISSUE', 'FIX', 'HOTFIX', 'PROBLEM') with
--      a simplified two-value enum ('INCIDENT', 'TASK'). All existing rows are
--      migrated to 'TASK' as a safe default via a USING clause.
--
--   2. Extends kaizen_ticket_priority_enum with operational priority levels
--      P1, P2, P3 to support incident severity classification.
--
--   3. Adds rca (Root Cause Analysis) and lessons_learned columns to
--      kaizen.tickets to capture incident post-mortem data.

-- =====================================================
-- 1. Replace kaizen_ticket_type_enum
-- =====================================================

-- Step 1: Rename the existing enum so the name is free for the new definition.
ALTER TYPE kaizen_ticket_type_enum RENAME TO kaizen_ticket_type_enum_old;

-- Step 2: Create the new enum with the updated value set.
CREATE TYPE kaizen_ticket_type_enum AS ENUM ('INCIDENT', 'TASK');

-- Step 3: Alter the column, casting all existing values to 'TASK'.
--         The USING clause coerces every legacy value unconditionally to 'TASK'.
--         An UPDATE-based migration is not possible here because the column type
--         must change atomically; the cast expression handles the remapping inline.
ALTER TABLE kaizen.tickets
    ALTER COLUMN type TYPE kaizen_ticket_type_enum
    USING 'TASK'::kaizen_ticket_type_enum;

-- Step 4: Drop the now-unused old enum.
DROP TYPE kaizen_ticket_type_enum_old;

-- =====================================================
-- 2. Extend kaizen_ticket_priority_enum with P1 / P2 / P3
-- =====================================================

ALTER TYPE kaizen_ticket_priority_enum ADD VALUE IF NOT EXISTS 'P1' AFTER 'HIGHEST';
ALTER TYPE kaizen_ticket_priority_enum ADD VALUE IF NOT EXISTS 'P2' AFTER 'P1';
ALTER TYPE kaizen_ticket_priority_enum ADD VALUE IF NOT EXISTS 'P3' AFTER 'P2';

-- =====================================================
-- 3. Add incident post-mortem columns to kaizen.tickets
-- =====================================================

ALTER TABLE kaizen.tickets
    ADD COLUMN rca TEXT NULL,
    ADD COLUMN lessons_learned TEXT NULL;
