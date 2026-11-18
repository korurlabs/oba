-- Village Database Schema - Add Iroh Bank Mutation Proposals
-- V1_2_17__Iroh_Add_Bank_Mutation_Proposals.sql
--
-- Adds the bank_mutation_proposals table that stores Claude-generated proposals for matching
-- or classifying imported bank mutations. Each proposal points at a single bank mutation,
-- carries Claude's suggested classification (MATCH_INVOICE, NEW_JOURNAL_ENTRY, or
-- REQUIRES_MANUAL_REVIEW), the structured proposal payload, the lifecycle status, and audit
-- trail of which user accepted or rejected it.
--
-- The table lives in the existing iroh schema and reuses project conventions:
--  - UUID primary keys with gen_random_uuid()
--  - TIMESTAMP WITH TIME ZONE for created_at / updated_at, defaulted to NOW()
--  - deleted BOOLEAN soft-delete column matching SoftDeleteBaseEntity
--  - organization_id FK to shrine.organizations
--  - VARCHAR(N) + CHECK constraints for type / confidence / status enums (matching the
--    pattern used by bank_mutations.status -- avoids ALTER TYPE ADD VALUE outside of
--    transactions when adding new enum members later)

-- =====================================================
-- Bank Mutation Proposals
-- =====================================================

CREATE TABLE iroh.bank_mutation_proposals (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL,
    bank_mutation_id        UUID NOT NULL,
    proposed_invoice_id     UUID,
    user_id                 UUID,
    type                    VARCHAR(30) NOT NULL,
    confidence              VARCHAR(10) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    proposed_journal_lines  JSONB,
    analysis_reason         TEXT,
    claude_raw_response     TEXT,
    user_action_time        TIMESTAMP WITHOUT TIME ZONE,
    user_reject_reason      VARCHAR(500),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                 BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT bank_mutation_proposals_pk PRIMARY KEY (id),

    CONSTRAINT bank_mutation_proposals_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),

    CONSTRAINT bank_mutation_proposals_bank_mutation_id_fk
        FOREIGN KEY (bank_mutation_id) REFERENCES iroh.bank_mutations(id),

    CONSTRAINT bank_mutation_proposals_proposed_invoice_id_fk
        FOREIGN KEY (proposed_invoice_id) REFERENCES iroh.invoices(id),

    CONSTRAINT bank_mutation_proposals_user_id_fk
        FOREIGN KEY (user_id) REFERENCES shrine.users(id),

    CONSTRAINT bank_mutation_proposals_type_check
        CHECK (type IN ('MATCH_INVOICE', 'NEW_JOURNAL_ENTRY', 'REQUIRES_MANUAL_REVIEW')),

    CONSTRAINT bank_mutation_proposals_confidence_check
        CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),

    CONSTRAINT bank_mutation_proposals_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'SUPERSEDED'))
);

-- FK indexes (recommended on every FK column to keep delete and join performance sane).
CREATE INDEX bank_mutation_proposals_organization_id_idx
    ON iroh.bank_mutation_proposals(organization_id);

CREATE INDEX bank_mutation_proposals_bank_mutation_id_idx
    ON iroh.bank_mutation_proposals(bank_mutation_id);

CREATE INDEX bank_mutation_proposals_proposed_invoice_id_idx
    ON iroh.bank_mutation_proposals(proposed_invoice_id);

CREATE INDEX bank_mutation_proposals_user_id_idx
    ON iroh.bank_mutation_proposals(user_id);

-- Composite index for the worklist query (pending proposals, newest first).
CREATE INDEX bank_mutation_proposals_org_status_created_at_idx
    ON iroh.bank_mutation_proposals(organization_id, status, created_at DESC);

-- Composite index for the proposal-history-per-mutation query.
CREATE INDEX bank_mutation_proposals_org_mutation_created_at_idx
    ON iroh.bank_mutation_proposals(organization_id, bank_mutation_id, created_at DESC);

-- Partial unique index ensuring at most one PENDING proposal per (organization, mutation).
-- Generating a fresh proposal first flips the prior PENDING to SUPERSEDED inside the same
-- transaction so this constraint is never violated by application code; the index protects
-- the invariant against concurrent analysis attempts and bugs.
CREATE UNIQUE INDEX bank_mutation_proposals_org_mutation_pending_uidx
    ON iroh.bank_mutation_proposals(organization_id, bank_mutation_id)
    WHERE status = 'PENDING' AND deleted = FALSE;
