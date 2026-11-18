-- Village Database Schema - Iroh Payment Allocation Phase 2
-- V1_2_26__Iroh_Payment_Allocation_Phase2.sql
--
-- Extends the payment allocation domain with Phase 2 features:
--
-- 1. Adds PARTIALLY_PAID to iroh_invoice_status_enum.
--    iroh_invoice_status_enum is a native PostgreSQL ENUM type (defined in
--    V1_2_2__Create_Iroh_Finance_Domain.sql). ALTER TYPE ... ADD VALUE is the
--    correct approach; no DROP/RECREATE cycle is needed.
--
-- 2. Adds unallocation audit columns to iroh.invoice_payment_allocations.
--    The existing SoftDeleteBaseEntity-inherited deleted column records whether
--    a row is soft-deleted; these two supplementary columns capture who performed
--    the unallocation and when, forming a complete audit trail.
--
-- flyway:executeInTransaction=false is required because ALTER TYPE ADD VALUE
-- cannot run inside a transaction in PostgreSQL.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Extend iroh_invoice_status_enum
-- =====================================================

-- Inserts PARTIALLY_PAID between OPEN and PAID in declaration order.
-- IF NOT EXISTS guards against accidental double-application.
ALTER TYPE iroh_invoice_status_enum ADD VALUE IF NOT EXISTS 'PARTIALLY_PAID' AFTER 'OPEN';

-- =====================================================
-- 2. Alter invoice_payment_allocations — audit columns
-- =====================================================

ALTER TABLE iroh.invoice_payment_allocations
    ADD COLUMN unallocated_by_user_id UUID,
    ADD COLUMN unallocated_at         TIMESTAMP WITH TIME ZONE;
