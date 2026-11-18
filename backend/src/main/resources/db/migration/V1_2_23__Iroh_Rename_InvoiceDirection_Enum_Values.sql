-- Village Database Schema - Iroh Rename InvoiceDirection Enum Values
-- V1_2_21__Iroh_Rename_InvoiceDirection_Enum_Values.sql
--
-- Renames the two values of iroh_invoice_direction_enum to align with
-- standard accounting terminology:
--   INCOME  → SALES     (outgoing invoices issued to customers)
--   EXPENSE → PURCHASE  (incoming invoices received from suppliers)
--
-- ALTER TYPE ... RENAME VALUE is a non-destructive, in-place rename
-- (PostgreSQL 10+). No table rewrites or data updates are required.

-- =====================================================
-- 1. Rename enum values
-- =====================================================

ALTER TYPE iroh_invoice_direction_enum RENAME VALUE 'INCOME'  TO 'SALES';
ALTER TYPE iroh_invoice_direction_enum RENAME VALUE 'EXPENSE' TO 'PURCHASE';
