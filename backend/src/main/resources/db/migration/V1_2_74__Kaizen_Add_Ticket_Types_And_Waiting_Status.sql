-- Database Schema - Kaizen Add Ticket Types And Waiting Status
-- V1_2_74__Kaizen_Add_Ticket_Types_And_Waiting_Status.sql
--
-- Tickets get five more types (BUG, TECH_DEBT, IMPROVEMENT, REFACTOR, PROBLEM)
-- and the general type TASK is renamed to FEATURE. PROBLEM, an issue that needs
-- research before it can be resolved, sits after INCIDENT in the enum order.
-- RENAME VALUE changes the enum label in place, so every existing TASK
-- ticket becomes FEATURE at once, without a table rewrite. A new status
-- WAITING_FOR_THIRD_PARTY marks work that is blocked on someone outside the
-- team; it sits after IN_PROGRESS in the enum order.
--
-- No statement uses a new value in this transaction, so the migration runs in
-- the default single transaction. No index or constraint changes: the columns
-- keep their types and defaults.

ALTER TYPE kaizen_ticket_type_enum RENAME VALUE 'TASK' TO 'FEATURE';

ALTER TYPE kaizen_ticket_type_enum ADD VALUE IF NOT EXISTS 'BUG';
ALTER TYPE kaizen_ticket_type_enum ADD VALUE IF NOT EXISTS 'TECH_DEBT';
ALTER TYPE kaizen_ticket_type_enum ADD VALUE IF NOT EXISTS 'IMPROVEMENT';
ALTER TYPE kaizen_ticket_type_enum ADD VALUE IF NOT EXISTS 'REFACTOR';
ALTER TYPE kaizen_ticket_type_enum ADD VALUE IF NOT EXISTS 'PROBLEM' AFTER 'INCIDENT';

ALTER TYPE kaizen_ticket_status_enum ADD VALUE IF NOT EXISTS 'WAITING_FOR_THIRD_PARTY' AFTER 'IN_PROGRESS';
