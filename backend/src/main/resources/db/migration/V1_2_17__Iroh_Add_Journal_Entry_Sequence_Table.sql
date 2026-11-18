-- Village Database Schema - Iroh Add Journal Entry Sequence Table
-- V1_2_15__Iroh_Add_Journal_Entry_Sequence_Table.sql
--
-- Adds the journal_entry_sequences counter table for the invoice-journal-proposal feature.
-- Each row tracks the last-used sequential number for one organisation in one calendar year.
-- The counter is incremented atomically via SELECT ... FOR UPDATE so concurrent proposal
-- requests from the same org cannot produce duplicate entry numbers.
--
-- Entry number format: JE-{YYYY}-{NNNN}  (e.g. JE-2026-0001)
-- Counter resets to 0001 on the first request of each new year.
-- Gaps are acceptable under Dutch accounting standards (uniqueness, not contiguity, is required).
--
-- No soft-delete column: this is a counter/sequence entity, not business data.
-- Uses BaseEntity pattern: UUID PK, created_at, updated_at.

-- =====================================================
-- 1. Create table
-- =====================================================

CREATE TABLE iroh.journal_entry_sequences (
    id              UUID     NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID     NOT NULL,
    year            SMALLINT NOT NULL,
    last_sequence   INTEGER  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT journal_entry_sequences_pk PRIMARY KEY (id),
    CONSTRAINT journal_entry_sequences_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT journal_entry_sequences_organization_id_year_uk
        UNIQUE (organization_id, year)
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Supports lookups by organisation when scanning all years for an org.
CREATE INDEX journal_entry_sequences_organization_id_idx
    ON iroh.journal_entry_sequences(organization_id);
