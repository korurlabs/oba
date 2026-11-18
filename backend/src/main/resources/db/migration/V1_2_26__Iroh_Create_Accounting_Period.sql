-- Village Database Schema - Iroh Create Accounting Period
-- V1_2_24__Iroh_Create_Accounting_Period.sql
--
-- Creates the accounting period domain for the Iroh finance module.
-- Accounting periods define contiguous date windows; once a period is CLOSED,
-- all dated writes (invoices, journal entries, bank mutation allocations) that
-- fall inside the window are blocked at the service layer via AccountingPeriodGuard.
--
-- 1. Creates the iroh_accounting_period_status_enum PostgreSQL enum.
-- 2. Creates the iroh.accounting_periods table with a date-range CHECK constraint.
--
-- Overlap prevention is enforced at the service layer. A native PostgreSQL
-- exclusion constraint (EXCLUDE USING gist) would require the btree_gist
-- extension; we intentionally do not rely on that extension.
--
-- flyway:executeInTransaction=false is required because ALTER TYPE ADD VALUE
-- (used in later migrations) cannot run inside a transaction. Listed here for
-- consistency with sibling migrations in this release.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_accounting_period_status_enum AS ENUM (
    'OPEN',
    'CLOSED',
    'REOPENED'
);

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE iroh.accounting_periods (
    id                   UUID                                  NOT NULL DEFAULT gen_random_uuid(),
    period_start         DATE                                  NOT NULL,
    period_end           DATE                                  NOT NULL,
    status               iroh_accounting_period_status_enum    NOT NULL DEFAULT 'OPEN',
    closed_by_user_id    UUID,
    closed_at            TIMESTAMP WITH TIME ZONE,
    reopened_by_user_id  UUID,
    reopened_at          TIMESTAMP WITH TIME ZONE,
    notes                VARCHAR(1000),
    organization_id      UUID                                  NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE              NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE              NOT NULL DEFAULT NOW(),
    deleted              BOOLEAN                               NOT NULL DEFAULT FALSE,
    CONSTRAINT accounting_periods_pk PRIMARY KEY (id),
    CONSTRAINT accounting_periods_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT accounting_periods_date_range_ck
        CHECK (period_start < period_end)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- FK support — every FK must be indexed.
CREATE INDEX accounting_periods_organization_id_idx
    ON iroh.accounting_periods(organization_id);

-- AccountingPeriodGuard query: find a CLOSED period that contains a given date.
-- Covers: WHERE organization_id = ? AND period_start <= ? AND period_end >= ? AND deleted = FALSE
CREATE INDEX accounting_periods_organization_id_dates_idx
    ON iroh.accounting_periods(organization_id, period_start, period_end)
    WHERE deleted = FALSE;

-- List view filter: periods by org and status.
CREATE INDEX accounting_periods_organization_id_status_idx
    ON iroh.accounting_periods(organization_id, status)
    WHERE deleted = FALSE;

-- Unique active period per (org, start, end) — prevents identical period re-creation.
-- Soft-deleted rows are excluded so a re-created period can reuse the same key.
CREATE UNIQUE INDEX accounting_periods_org_start_end_uidx
    ON iroh.accounting_periods(organization_id, period_start, period_end)
    WHERE deleted = FALSE;
