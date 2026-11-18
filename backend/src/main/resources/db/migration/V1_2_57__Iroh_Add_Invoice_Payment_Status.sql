-- Database Schema - Add Invoice Payment Status
-- V1_2_57__Iroh_Add_Invoice_Payment_Status.sql
--
-- Adds a dedicated payment_status column to iroh.invoices to track
-- payment settlement independently of the lifecycle status column.
-- Backfills from the current status values before the status enum
-- is rewritten in V1_2_58 / V1_2_59.
--
-- Ordering: MUST run before V1_2_58 and V1_2_59.
-- The backfill in Step C reads status values 'PARTIALLY_PAID' and 'PAID'
-- which are removed from the enum in V1_2_59.

-- =====================================================
-- 1. Create enum
-- =====================================================

CREATE TYPE iroh_invoice_payment_status_enum AS ENUM ('NOT_PAID', 'PARTIALLY_PAID', 'PAID');

-- =====================================================
-- 2. Add column
-- =====================================================

-- Add with DEFAULT so all existing rows get NOT_PAID without a separate UPDATE.
ALTER TABLE iroh.invoices
    ADD COLUMN payment_status iroh_invoice_payment_status_enum NOT NULL DEFAULT 'NOT_PAID';

-- =====================================================
-- 3. Backfill from existing status values
-- =====================================================

-- Rows where status = 'PARTIALLY_PAID' map to payment_status = 'PARTIALLY_PAID'.
UPDATE iroh.invoices SET payment_status = 'PARTIALLY_PAID' WHERE status = 'PARTIALLY_PAID';

-- Rows where status = 'PAID' map to payment_status = 'PAID'.
UPDATE iroh.invoices SET payment_status = 'PAID' WHERE status = 'PAID';

-- OPEN, DRAFT, and CANCELLED rows already carry NOT_PAID from the column default.

-- =====================================================
-- 4. Create indexes
-- =====================================================

CREATE INDEX invoices_organization_id_payment_status_idx
    ON iroh.invoices(organization_id, payment_status);
