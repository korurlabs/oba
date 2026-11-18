-- Database Schema - Clean Up Invoice Status Enum
-- V1_2_59__Iroh_Cleanup_Invoice_Status_Enum.sql
--
-- Renames the lifecycle status values on iroh.invoices:
--   OPEN          -> SUBMITTED
--   PARTIALLY_PAID -> SUBMITTED
--   PAID          -> POSTED
--   DRAFT         -> DRAFT    (unchanged)
--   CANCELLED     -> CANCELLED (unchanged)
--
-- Then drops and recreates iroh_invoice_status_enum with only the four
-- target values: DRAFT, SUBMITTED, POSTED, CANCELLED.
--
-- Strategy: retype the column to TEXT, drop the old enum, recreate it
-- with the target values, then restore the column type using a USING clause.
-- The composite index invoices_organization_id_status_idx is rebuilt
-- automatically by PostgreSQL when the column type changes.
--
-- Ordering: MUST run after V1_2_57 (payment_status backfill) and V1_2_58
-- (SUBMITTED + POSTED added to the enum).

-- =====================================================
-- 1. Backfill rows to new status values
-- =====================================================

UPDATE iroh.invoices SET status = 'SUBMITTED' WHERE status IN ('OPEN', 'PARTIALLY_PAID');
UPDATE iroh.invoices SET status = 'POSTED'    WHERE status = 'PAID';

-- =====================================================
-- 2. Retype column to TEXT so the old enum type can be dropped
-- =====================================================

-- Drop the DEFAULT first: the default value is typed against the old enum,
-- so PostgreSQL will refuse to DROP TYPE while it exists.
ALTER TABLE iroh.invoices ALTER COLUMN status DROP DEFAULT;

ALTER TABLE iroh.invoices ALTER COLUMN status TYPE TEXT;

-- =====================================================
-- 3. Drop old enum type
-- =====================================================

DROP TYPE iroh_invoice_status_enum;

-- =====================================================
-- 4. Recreate enum with only the four target values
-- =====================================================

CREATE TYPE iroh_invoice_status_enum AS ENUM ('DRAFT', 'SUBMITTED', 'POSTED', 'CANCELLED');

-- =====================================================
-- 5. Restore column type and constraints
-- =====================================================

-- The USING clause casts the TEXT values to the new enum.
-- All rows now contain only 'DRAFT', 'SUBMITTED', 'POSTED', or 'CANCELLED'.
ALTER TABLE iroh.invoices
    ALTER COLUMN status TYPE iroh_invoice_status_enum
    USING status::iroh_invoice_status_enum;

ALTER TABLE iroh.invoices ALTER COLUMN status SET NOT NULL;
ALTER TABLE iroh.invoices ALTER COLUMN status SET DEFAULT 'DRAFT';
