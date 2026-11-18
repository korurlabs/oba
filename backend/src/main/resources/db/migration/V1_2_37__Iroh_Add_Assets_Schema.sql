-- Village Database Schema - Iroh Assets and Depreciation
-- V1_2_37__Iroh_Add_Assets_Schema.sql
--
-- Adds asset management and depreciation tracking to Iroh finance domain.
-- Creates two tables:
-- 1. iroh.assets — fixed and intangible assets with depreciation configuration
-- 2. iroh.depreciation_entries — per-asset per-fiscal-year depreciation schedule
--
-- Features:
-- - Straight-line and declining-balance depreciation methods
-- - Auto-posting of depreciation entries to general ledger via journal entries
-- - Ledger account references for depreciation expense and accumulated depreciation
-- - Soft-delete support for both entities

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_asset_type_enum AS ENUM ('TANGIBLE', 'INTANGIBLE');
CREATE TYPE iroh_asset_status_enum AS ENUM ('ACTIVE', 'DISPOSED', 'SOLD');
CREATE TYPE iroh_depreciation_method_enum AS ENUM ('STRAIGHT_LINE', 'DECLINING_BALANCE');
CREATE TYPE iroh_depreciation_status_enum AS ENUM ('DRAFT', 'POSTED');

-- =====================================================
-- 2. Create iroh.assets
-- =====================================================

CREATE TABLE iroh.assets (
    id                                  UUID                NOT NULL DEFAULT gen_random_uuid(),
    organization_id                     UUID                NOT NULL,
    name                                VARCHAR(255)        NOT NULL,
    description                         TEXT,
    asset_type                          iroh_asset_type_enum        NOT NULL,
    serial_number                       VARCHAR(100),
    acquisition_date                    DATE                NOT NULL,
    acquisition_cost                    NUMERIC(19, 2)      NOT NULL,
    useful_life_years                   INT                 NOT NULL,
    residual_value                      NUMERIC(19, 2)      NOT NULL DEFAULT 0,
    depreciation_method                 iroh_depreciation_method_enum NOT NULL,
    status                              iroh_asset_status_enum      NOT NULL DEFAULT 'ACTIVE',
    disposal_date                       DATE,
    expense_ledger_account_id           UUID,
    accumulated_ledger_account_id       UUID,
    created_at                          TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    deleted                             BOOLEAN             NOT NULL DEFAULT FALSE,
    CONSTRAINT assets_pk PRIMARY KEY (id),
    CONSTRAINT assets_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT assets_expense_ledger_account_id_fk
        FOREIGN KEY (expense_ledger_account_id) REFERENCES iroh.ledger_accounts(id) ON DELETE SET NULL,
    CONSTRAINT assets_accumulated_ledger_account_id_fk
        FOREIGN KEY (accumulated_ledger_account_id) REFERENCES iroh.ledger_accounts(id) ON DELETE SET NULL,
    CONSTRAINT assets_useful_life_years_ck CHECK (useful_life_years > 0),
    CONSTRAINT assets_acquisition_cost_ck CHECK (acquisition_cost > 0),
    CONSTRAINT assets_residual_value_ck CHECK (residual_value >= 0)
);

-- =====================================================
-- 3. Create iroh.depreciation_entries
-- =====================================================

CREATE TABLE iroh.depreciation_entries (
    id                                  UUID                NOT NULL DEFAULT gen_random_uuid(),
    organization_id                     UUID                NOT NULL,
    asset_id                            UUID                NOT NULL,
    fiscal_year                         INT                 NOT NULL,
    depreciation_amount                 NUMERIC(19, 2)      NOT NULL,
    accumulated_depreciation            NUMERIC(19, 2)      NOT NULL,
    book_value                          NUMERIC(19, 2)      NOT NULL,
    status                              iroh_depreciation_status_enum NOT NULL DEFAULT 'DRAFT',
    journal_entry_id                    UUID,
    created_at                          TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    deleted                             BOOLEAN             NOT NULL DEFAULT FALSE,
    CONSTRAINT depreciation_entries_pk PRIMARY KEY (id),
    CONSTRAINT depreciation_entries_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT depreciation_entries_asset_id_fk
        FOREIGN KEY (asset_id) REFERENCES iroh.assets(id) ON DELETE CASCADE,
    CONSTRAINT depreciation_entries_journal_entry_id_fk
        FOREIGN KEY (journal_entry_id) REFERENCES iroh.journal_entries(id) ON DELETE SET NULL
);

-- Partial uniqueness on (asset_id, fiscal_year) limited to live rows.
-- Implemented as a partial UNIQUE INDEX rather than an inline UNIQUE
-- CONSTRAINT because PostgreSQL only supports the WHERE clause on indexes,
-- not on table-level constraints.
CREATE UNIQUE INDEX depreciation_entries_asset_id_fiscal_year_uk
    ON iroh.depreciation_entries(asset_id, fiscal_year)
    WHERE deleted = FALSE;

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- FK support indexes
CREATE INDEX assets_organization_id_idx
    ON iroh.assets(organization_id);

CREATE INDEX depreciation_entries_organization_id_idx
    ON iroh.depreciation_entries(organization_id);

CREATE INDEX depreciation_entries_asset_id_idx
    ON iroh.depreciation_entries(asset_id);

-- Partial indexes for soft-delete filtered queries
CREATE INDEX assets_organization_id_status_idx
    ON iroh.assets(organization_id, status)
    WHERE deleted = FALSE;

CREATE INDEX depreciation_entries_organization_id_status_idx
    ON iroh.depreciation_entries(organization_id, status)
    WHERE deleted = FALSE;

-- Asset-year lookup (common in reporting)
CREATE INDEX depreciation_entries_asset_id_fiscal_year_idx
    ON iroh.depreciation_entries(asset_id, fiscal_year)
    WHERE deleted = FALSE;
