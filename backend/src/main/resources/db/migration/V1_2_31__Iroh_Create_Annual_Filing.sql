-- Village Database Schema - Iroh Create Annual Filing
-- V1_2_29__Iroh_Create_Annual_Filing.sql
--
-- Introduces the annual filing (jaaraangifte) feature to the Iroh finance domain.
-- Dutch companies must submit one or more annual filings per fiscal year depending
-- on their legal form: personal income tax (IB), corporate income tax (VPB), and/or
-- annual accounts (jaarrekening) deposited with the KvK.
--
-- 1) iroh_annual_filing_type_enum   — IB_RETURN, VPB_RETURN, ANNUAL_ACCOUNTS
-- 2) iroh_annual_filing_status_enum — DRAFT, IN_REVIEW, SUBMITTED, FILED
-- 3) iroh.annual_filings            — one row per (organization, fiscal year, type);
--                                     links optionally to iroh.compliance_obligations
--                                     and carries type-specific data in a JSONB column.
--
-- Soft-delete is implemented via the `deleted BOOLEAN NOT NULL DEFAULT FALSE` column,
-- consistent with SoftDeleteBaseEntity used elsewhere in the Iroh domain.
--
-- flyway:executeInTransaction=false is required because PostgreSQL's CREATE TYPE
-- cannot run inside a transaction in all scenarios; kept consistent with sibling
-- Iroh migrations.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_annual_filing_type_enum AS ENUM (
    'IB_RETURN',
    'VPB_RETURN',
    'ANNUAL_ACCOUNTS'
);

CREATE TYPE iroh_annual_filing_status_enum AS ENUM (
    'DRAFT',
    'IN_REVIEW',
    'SUBMITTED',
    'FILED'
);

-- =====================================================
-- 2. Create iroh.annual_filings
-- =====================================================

CREATE TABLE iroh.annual_filings (
    id                        UUID                              NOT NULL DEFAULT gen_random_uuid(),
    organization_id           UUID                              NOT NULL,
    fiscal_year               INTEGER                           NOT NULL,
    type                      iroh_annual_filing_type_enum      NOT NULL,
    status                    iroh_annual_filing_status_enum    NOT NULL DEFAULT 'DRAFT',
    taxable_income            NUMERIC(15,2),
    tax_owed                  NUMERIC(15,2),
    submitted_at              TIMESTAMP WITH TIME ZONE,
    submitted_reference       VARCHAR(255),
    details                   JSONB,
    compliance_obligation_id  UUID,
    created_by_id             UUID,
    updated_by_id             UUID,
    created_at                TIMESTAMP WITH TIME ZONE          NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE          NOT NULL DEFAULT NOW(),
    deleted                   BOOLEAN                           NOT NULL DEFAULT FALSE,
    CONSTRAINT annual_filings_pk PRIMARY KEY (id),
    CONSTRAINT annual_filings_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE CASCADE,
    CONSTRAINT annual_filings_compliance_obligation_id_fk
        FOREIGN KEY (compliance_obligation_id)
        REFERENCES iroh.compliance_obligations(id)
        ON DELETE SET NULL,
    CONSTRAINT annual_filings_fiscal_year_ck
        CHECK (fiscal_year >= 2000)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- FK support — organization_id is the primary partition key for all queries.
CREATE INDEX annual_filings_organization_id_idx
    ON iroh.annual_filings(organization_id);

-- FK support — compliance_obligation_id is nullable but must still be indexed.
CREATE INDEX annual_filings_compliance_obligation_id_idx
    ON iroh.annual_filings(compliance_obligation_id)
    WHERE compliance_obligation_id IS NOT NULL;

-- List view filter: all non-deleted filings for an org ordered by year desc.
CREATE INDEX annual_filings_organization_id_fiscal_year_idx
    ON iroh.annual_filings(organization_id, fiscal_year DESC)
    WHERE deleted = FALSE;

-- Filter by type within an organization (e.g. show only IB_RETURN filings).
CREATE INDEX annual_filings_organization_id_type_idx
    ON iroh.annual_filings(organization_id, type)
    WHERE deleted = FALSE;

-- Filter by status within an organization (e.g. show only DRAFT filings).
CREATE INDEX annual_filings_organization_id_status_idx
    ON iroh.annual_filings(organization_id, status)
    WHERE deleted = FALSE;
