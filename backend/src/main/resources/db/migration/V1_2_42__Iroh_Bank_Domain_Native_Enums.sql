-- Village Database Schema - Iroh Bank Domain Native Enums
-- V1_2_42__Iroh_Bank_Domain_Native_Enums.sql
--
-- Converts the bank domain status / type / confidence columns from VARCHAR + CHECK constraints
-- to native PostgreSQL ENUM types so the schema is type-safe at the database level and matches
-- the project's enum convention (see V1_2_2__Create_Iroh_Finance_Domain.sql, V1_2_6 etc.).
--
-- Tables and columns affected:
--   iroh.bank_mutations.status              -> iroh_bank_mutation_status_enum
--   iroh.bank_mutation_proposals.type       -> iroh_proposal_type_enum
--   iroh.bank_mutation_proposals.confidence -> iroh_proposal_confidence_level_enum
--   iroh.bank_mutation_proposals.status     -> iroh_proposal_status_enum
--
-- Steps per column:
--   1. Drop the CHECK constraint that guarded the VARCHAR values.
--   2. Drop any partial indexes that filter on the VARCHAR literal (cannot survive the type
--      change in place).
--   3. Create the native ENUM type.
--   4. ALTER COLUMN TYPE ... USING column::enum_type to cast existing rows.
--   5. Recreate any dropped partial indexes against the new enum-typed column.

-- =====================================================
-- 1. Create native ENUM types
-- =====================================================

CREATE TYPE iroh_bank_mutation_status_enum AS ENUM (
    'UNMATCHED',
    'PROPOSED',
    'MATCHED',
    'IGNORED',
    'RECONCILIATION_MATCHED'
);

CREATE TYPE iroh_proposal_type_enum AS ENUM (
    'MATCH_INVOICE',
    'NEW_JOURNAL_ENTRY',
    'REQUIRES_MANUAL_REVIEW'
);

CREATE TYPE iroh_proposal_confidence_level_enum AS ENUM (
    'HIGH',
    'MEDIUM',
    'LOW'
);

CREATE TYPE iroh_proposal_status_enum AS ENUM (
    'PENDING',
    'ACCEPTED',
    'REJECTED',
    'EXPIRED',
    'SUPERSEDED'
);

-- =====================================================
-- 2. Convert iroh.bank_mutations.status -> iroh_bank_mutation_status_enum
-- =====================================================

-- Drop the CHECK constraint (last redefined in V1_2_27 to include RECONCILIATION_MATCHED).
ALTER TABLE iroh.bank_mutations
    DROP CONSTRAINT bank_mutations_status_chk;

ALTER TABLE iroh.bank_mutations
    ALTER COLUMN status TYPE iroh_bank_mutation_status_enum
    USING status::iroh_bank_mutation_status_enum;

-- =====================================================
-- 3. Convert iroh.bank_mutation_proposals.type -> iroh_proposal_type_enum
-- =====================================================

ALTER TABLE iroh.bank_mutation_proposals
    DROP CONSTRAINT bank_mutation_proposals_type_check;

ALTER TABLE iroh.bank_mutation_proposals
    ALTER COLUMN type TYPE iroh_proposal_type_enum
    USING type::iroh_proposal_type_enum;

-- =====================================================
-- 4. Convert iroh.bank_mutation_proposals.confidence -> iroh_proposal_confidence_level_enum
-- =====================================================

ALTER TABLE iroh.bank_mutation_proposals
    DROP CONSTRAINT bank_mutation_proposals_confidence_check;

ALTER TABLE iroh.bank_mutation_proposals
    ALTER COLUMN confidence TYPE iroh_proposal_confidence_level_enum
    USING confidence::iroh_proposal_confidence_level_enum;

-- =====================================================
-- 5. Convert iroh.bank_mutation_proposals.status -> iroh_proposal_status_enum
-- =====================================================

-- Drop the partial unique index that filters on status = 'PENDING' (the VARCHAR literal would
-- be invalid against the new enum type).
DROP INDEX IF EXISTS iroh.bank_mutation_proposals_org_mutation_pending_uidx;

ALTER TABLE iroh.bank_mutation_proposals
    DROP CONSTRAINT bank_mutation_proposals_status_check;

ALTER TABLE iroh.bank_mutation_proposals
    ALTER COLUMN status TYPE iroh_proposal_status_enum
    USING status::iroh_proposal_status_enum;

-- Recreate the partial unique index against the enum-typed column. Cast the literal to the
-- enum type to keep the predicate strictly typed.
CREATE UNIQUE INDEX bank_mutation_proposals_org_mutation_pending_uidx
    ON iroh.bank_mutation_proposals(organization_id, bank_mutation_id)
    WHERE status = 'PENDING'::iroh_proposal_status_enum AND deleted = FALSE;
