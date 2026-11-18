-- Village Database Schema - Iroh VAT Filing Audit and Immutability
-- V1_2_40__Iroh_Vat_Filing_Audit_And_Immutability.sql
--
-- Strengthens the VAT filing domain with three compliance-critical improvements:
--
-- 1. Audit metadata on vat_returns:
--    - submitted_by_user_id  — Keycloak user UUID who triggered the filing.
--    - filing_channel        — How the return was filed (MANUAL_UPLOAD, AUTOMATIC_API, etc.).
--    - confirmation_number   — Reference issued by the tax authority after acceptance.
--    - accounting_period_id  — FK to iroh.accounting_periods for period-locking integration.
--
-- 2. Immutability trigger on vat_returns:
--    A BEFORE UPDATE trigger prevents any column from being changed once the return has
--    left DRAFT status. The service layer already enforces this rule; the trigger is
--    defence-in-depth that survives direct SQL, admin scripts, and future code regressions.
--    Only the status column transition OUT of DRAFT is checked — once SUBMITTED/ACCEPTED/
--    REJECTED, no field (including status) may change.
--
-- 3. Audit metadata on vat_supplements (mirrors vat_returns):
--    - submitted_by_user_id  — who filed the supplement.
--    - filing_channel        — how it was filed.
--    - confirmation_number   — tax authority reference.
--
-- No data migrations required — all new columns are nullable or have safe defaults.
--
-- Rollback (manual, in reverse order):
--   DROP TRIGGER IF EXISTS trg_vat_returns_immutability ON iroh.vat_returns;
--   DROP FUNCTION IF EXISTS iroh.enforce_vat_return_immutability();
--   ALTER TABLE iroh.vat_supplements
--       DROP COLUMN IF EXISTS submitted_by_user_id,
--       DROP COLUMN IF EXISTS filing_channel,
--       DROP COLUMN IF EXISTS confirmation_number;
--   DROP INDEX IF EXISTS vat_returns_accounting_period_id_idx;
--   ALTER TABLE iroh.vat_returns
--       DROP CONSTRAINT IF EXISTS vat_returns_accounting_period_id_fk,
--       DROP COLUMN IF EXISTS submitted_by_user_id,
--       DROP COLUMN IF EXISTS filing_channel,
--       DROP COLUMN IF EXISTS confirmation_number,
--       DROP COLUMN IF EXISTS accounting_period_id;

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create filing_channel enum
-- =====================================================

CREATE TYPE iroh_filing_channel_enum AS ENUM (
    'MANUAL_UPLOAD',
    'AUTOMATIC_API',
    'MANUAL_PORTAL',
    'CORRECTION'
);

-- =====================================================
-- 2. Add audit columns to vat_returns
-- =====================================================

-- Column order follows the table convention: attributes before FK columns.
-- accounting_period_id is placed with FK columns (after audit scalars).

ALTER TABLE iroh.vat_returns
    ADD COLUMN submitted_by_user_id UUID,
    ADD COLUMN filing_channel       iroh_filing_channel_enum,
    ADD COLUMN confirmation_number  VARCHAR(100),
    ADD COLUMN accounting_period_id UUID;

-- FK constraint for accounting_period_id.
-- Nullable — not every VAT return is explicitly linked to an accounting period at MVP.
-- No ON DELETE CASCADE: period closure is a business event; deleting the period record
-- should not silently erase the VAT return's linkage — use RESTRICT to surface the issue.
ALTER TABLE iroh.vat_returns
    ADD CONSTRAINT vat_returns_accounting_period_id_fk
        FOREIGN KEY (accounting_period_id)
        REFERENCES iroh.accounting_periods(id)
        ON DELETE RESTRICT;

-- =====================================================
-- 3. Index the new FK on vat_returns
-- =====================================================

-- Required by convention: every FK column must be indexed.
-- Also supports "which VAT returns reference this accounting period?" look-ups.
CREATE INDEX vat_returns_accounting_period_id_idx
    ON iroh.vat_returns(accounting_period_id)
    WHERE accounting_period_id IS NOT NULL;

-- =====================================================
-- 4. Immutability trigger on vat_returns
-- =====================================================

-- Trigger function: block any UPDATE on a vat_returns row that is no longer in DRAFT.
-- The check covers ALL columns — once filed, the row is append-only from the DB's point
-- of view. The only legitimate post-DRAFT write is setting confirmation_number when the
-- tax authority responds, which must go through the service layer (and if needed the
-- trigger can be refined to allow that single column later without opening everything).
CREATE OR REPLACE FUNCTION iroh.enforce_vat_return_immutability()
RETURNS TRIGGER AS $$
BEGIN
    -- Allow the update only when the row is still in DRAFT.
    -- Any other status means the return has been submitted and must not change.
    IF OLD.status::TEXT != 'DRAFT' THEN
        RAISE EXCEPTION
            'VAT return % is immutable: status is %. Only DRAFT returns may be modified. '
            'Use a vat_supplement to correct a submitted return.',
            OLD.id,
            OLD.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- BEFORE UPDATE so the exception fires before any change reaches storage.
-- FOR EACH ROW because the guard is per-record (different returns have different statuses).
CREATE TRIGGER trg_vat_returns_immutability
    BEFORE UPDATE ON iroh.vat_returns
    FOR EACH ROW
    EXECUTE FUNCTION iroh.enforce_vat_return_immutability();

-- =====================================================
-- 5. Add audit columns to vat_supplements
-- =====================================================

-- Mirrors the vat_returns audit columns for symmetry and the same compliance needs.
-- filing_channel uses the same shared enum — a supplement is filed through the same
-- channels as an original return.

ALTER TABLE iroh.vat_supplements
    ADD COLUMN submitted_by_user_id UUID,
    ADD COLUMN filing_channel       iroh_filing_channel_enum,
    ADD COLUMN confirmation_number  VARCHAR(100);
