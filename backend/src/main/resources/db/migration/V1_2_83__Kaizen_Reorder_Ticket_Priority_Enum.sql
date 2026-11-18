-- Village Database Schema - Kaizen: Reorder Ticket Priority Enum
-- V1_2_83__Kaizen_Reorder_Ticket_Priority_Enum.sql
--
-- PostgreSQL sorts an enum column by the declaration order of its values, not by
-- name. The values were declared LOWEST, LOW, MEDIUM, HIGH, HIGHEST, P1, P2, P3
-- (P1 to P3 were appended later), so sorting by priority ranked P3 above P1 and
-- P1 above HIGHEST. This migration rebuilds kaizen_ticket_priority_enum so that
-- the declaration order is the urgency order, least to most urgent:
--
--   LOWEST, LOW, MEDIUM, P3, HIGH, HIGHEST, P2, P1
--
-- ASC then gives least urgent first and DESC gives P1, P2, HIGHEST, HIGH, P3,
-- MEDIUM, LOW, LOWEST. Every existing value maps one to one, so no row changes.

-- Step 1: Rename the existing enum so the name is free for the new definition.
ALTER TYPE kaizen_ticket_priority_enum RENAME TO kaizen_ticket_priority_enum_old;

-- Step 2: Create the new enum with the values in urgency order.
CREATE TYPE kaizen_ticket_priority_enum AS ENUM ('LOWEST', 'LOW', 'MEDIUM', 'P3', 'HIGH', 'HIGHEST', 'P2', 'P1');

-- Step 3: Drop the column default. It is typed with the old enum and cannot be
--         cast automatically, so it would make the type change below fail.
ALTER TABLE kaizen.tickets
    ALTER COLUMN priority DROP DEFAULT;

-- Step 4: Change the column type. The text round trip maps each value by name.
--         PostgreSQL rebuilds tickets_priority_idx as part of this statement.
ALTER TABLE kaizen.tickets
    ALTER COLUMN priority TYPE kaizen_ticket_priority_enum
    USING priority::text::kaizen_ticket_priority_enum;

-- Step 5: Restore the default; the column stays NOT NULL.
ALTER TABLE kaizen.tickets
    ALTER COLUMN priority SET DEFAULT 'MEDIUM'::kaizen_ticket_priority_enum;

-- Step 6: Drop the now-unused old enum.
DROP TYPE kaizen_ticket_priority_enum_old;
