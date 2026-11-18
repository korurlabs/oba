-- Village Database Schema - Iroh Add Bank Reconciliation
-- V1_2_25__Iroh_Add_Bank_Reconciliation.sql
--
-- Adds the bank reconciliation sub-domain to the Iroh finance module.
--
-- 1. Adds reconciliation_journal_entry_line_id and last_reconciliation_checkpoint_id
--    columns to iroh.bank_mutations. Both are nullable UUIDs stored as raw IDs
--    (not FK columns) to avoid eager-loading complications at the entity layer.
-- 2. Extends the bank_mutations_status_chk CHECK constraint to include the new
--    RECONCILIATION_MATCHED value. Because bank_mutations.status is a VARCHAR(20)
--    with a named CHECK constraint (not a PostgreSQL ENUM type), the constraint
--    must be dropped and re-created rather than using ALTER TYPE ADD VALUE.
-- 3. Creates the iroh.bank_reconciliation_checkpoints table. Checkpoints are
--    persistent point-in-time records that accumulate over time; they are never
--    automatically deleted.

-- =====================================================
-- 1. Alter bank_mutations — reconciliation columns
-- =====================================================

ALTER TABLE iroh.bank_mutations
    ADD COLUMN reconciliation_journal_entry_line_id UUID,
    ADD COLUMN last_reconciliation_checkpoint_id    UUID;

-- =====================================================
-- 2. Extend bank_mutations status CHECK constraint
-- =====================================================

-- Drop the existing constraint and re-create it with the additional RECONCILIATION_MATCHED value.
-- The status column is VARCHAR(20) backed by a named CHECK constraint, not a PostgreSQL ENUM type.
ALTER TABLE iroh.bank_mutations
    DROP CONSTRAINT bank_mutations_status_chk;

ALTER TABLE iroh.bank_mutations
    ADD CONSTRAINT bank_mutations_status_chk
        CHECK (status IN ('UNMATCHED', 'PROPOSED', 'MATCHED', 'IGNORED', 'RECONCILIATION_MATCHED'));

-- Index for reconciliation match look-up: "which mutations reference this journal entry line?"
-- Sparse — only rows where reconciliation_journal_entry_line_id IS NOT NULL are included.
CREATE INDEX bank_mutations_reconciliation_journal_entry_line_id_idx
    ON iroh.bank_mutations(reconciliation_journal_entry_line_id)
    WHERE reconciliation_journal_entry_line_id IS NOT NULL;

-- Index for checkpoint look-up: "which mutations belong to a given checkpoint?"
CREATE INDEX bank_mutations_last_reconciliation_checkpoint_id_idx
    ON iroh.bank_mutations(last_reconciliation_checkpoint_id)
    WHERE last_reconciliation_checkpoint_id IS NOT NULL;

-- =====================================================
-- 3. Create bank_reconciliation_checkpoints table
-- =====================================================

CREATE TABLE iroh.bank_reconciliation_checkpoints (
    id                    UUID             NOT NULL DEFAULT gen_random_uuid(),
    reconciliation_date   DATE             NOT NULL,
    reconciled_by_user_id UUID             NOT NULL,
    reconciled_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    total_mutations       NUMERIC(19,4)    NOT NULL,
    total_postings        NUMERIC(19,4)    NOT NULL,
    difference            NUMERIC(19,4)    NOT NULL,
    is_balanced           BOOLEAN          NOT NULL,
    notes                 VARCHAR(1000),
    organization_id       UUID             NOT NULL,
    bank_account_id       UUID             NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted               BOOLEAN          NOT NULL DEFAULT FALSE,
    CONSTRAINT bank_reconciliation_checkpoints_pk PRIMARY KEY (id),
    CONSTRAINT bank_reconciliation_checkpoints_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT bank_reconciliation_checkpoints_bank_account_id_fk
        FOREIGN KEY (bank_account_id) REFERENCES iroh.bank_accounts(id)
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- FK support.
CREATE INDEX bank_reconciliation_checkpoints_organization_id_idx
    ON iroh.bank_reconciliation_checkpoints(organization_id);

CREATE INDEX bank_reconciliation_checkpoints_bank_account_id_idx
    ON iroh.bank_reconciliation_checkpoints(bank_account_id);

-- "Find the most recent checkpoint for a bank account" — the primary query on this table.
-- Ordered DESC so the planner can satisfy ORDER BY reconciliation_date DESC LIMIT 1 via index scan.
CREATE INDEX bank_reconciliation_checkpoints_bank_account_id_date_idx
    ON iroh.bank_reconciliation_checkpoints(bank_account_id, reconciliation_date DESC)
    WHERE deleted = FALSE;

-- Unique partial index: one active checkpoint per (bank account, reconciliation date).
-- Prevents duplicate checkpoint records for the same date on the same account.
CREATE UNIQUE INDEX bank_reconciliation_checkpoints_bank_account_id_date_uidx
    ON iroh.bank_reconciliation_checkpoints(bank_account_id, reconciliation_date)
    WHERE deleted = FALSE;
