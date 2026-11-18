-- Village Database Schema - Iroh Add ICP Declaration
-- V1_2_38__Iroh_Add_Icp_Declaration.sql
--
-- Introduces Intracommunautaire Prestaties (ICP) declarations to the Iroh finance domain.
-- Dutch companies must submit ICP declarations to the Dutch Tax Authority (Belastingdienst)
-- for intra-EU supplies of goods (leveringen), services (diensten), and triangulation (ABC).
--
-- 1) Extends iroh_vat_period_enum with monthly values M1..M12 (ICP can be filed monthly
--    for companies exceeding the quarterly threshold).
-- 2) iroh_icp_declaration_status_enum — DRAFT, SUBMITTED, ACCEPTED, REJECTED
-- 3) iroh_icp_supply_type_enum        — L (leveringen/goods), D (diensten/services), T (triangulation)
-- 4) iroh.icp_declarations            — one row per (organization, year, period, version);
--                                       version allows correction filings (v2, v3, ...).
-- 5) iroh.icp_declaration_lines       — individual EU counterparty lines per declaration.
--
-- Soft-delete is implemented via `deleted BOOLEAN NOT NULL DEFAULT FALSE`,
-- consistent with SoftDeleteBaseEntity used throughout the Iroh domain.
--
-- flyway:executeInTransaction=false is required because PostgreSQL's ALTER TYPE ADD VALUE
-- and CREATE TYPE cannot run inside a transaction in all scenarios.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Extend iroh_vat_period_enum with monthly values
-- =====================================================

-- iroh_vat_period_enum was created in V1_2_6 with Q1-Q4 only.
-- ICP filers above the quarterly threshold must file monthly; add M1..M12.

ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M1';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M2';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M3';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M4';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M5';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M6';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M7';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M8';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M9';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M10';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M11';
ALTER TYPE iroh_vat_period_enum ADD VALUE IF NOT EXISTS 'M12';

-- =====================================================
-- 2. Create ICP-specific enum types
-- =====================================================

-- Separate status enum for ICP declarations (semantically distinct from VAT return status,
-- even though the values are currently identical — decouples future evolution).
CREATE TYPE iroh_icp_declaration_status_enum AS ENUM (
    'DRAFT',
    'SUBMITTED',
    'ACCEPTED',
    'REJECTED'
);

-- Supply type: L = leveringen (goods), D = diensten (services), T = triangulation (ABC deals).
CREATE TYPE iroh_icp_supply_type_enum AS ENUM (
    'L',
    'D',
    'T'
);

-- =====================================================
-- 3. Create iroh.icp_declarations
-- =====================================================

CREATE TABLE iroh.icp_declarations (
    id               UUID                               NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID                               NOT NULL,
    year             INT                                NOT NULL,
    period           iroh_vat_period_enum               NOT NULL,
    status           iroh_icp_declaration_status_enum   NOT NULL DEFAULT 'DRAFT',
    version          INT                                NOT NULL DEFAULT 1,
    total_amount     NUMERIC(15,2)                      NOT NULL DEFAULT 0,
    submitted_at     TIMESTAMP WITH TIME ZONE,
    created_by_id    UUID,
    updated_by_id    UUID,
    created_at       TIMESTAMP WITH TIME ZONE           NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE           NOT NULL DEFAULT NOW(),
    deleted          BOOLEAN                            NOT NULL DEFAULT FALSE,
    CONSTRAINT icp_declarations_pk
        PRIMARY KEY (id),
    CONSTRAINT icp_declarations_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE RESTRICT,
    CONSTRAINT icp_declarations_year_ck
        CHECK (year >= 2020 AND year <= 2099),
    CONSTRAINT icp_declarations_version_ck
        CHECK (version >= 1),
    CONSTRAINT icp_declarations_total_amount_ck
        CHECK (total_amount >= 0)
);

-- One active declaration per (org, year, period) — soft-deleted rows are excluded
-- so a re-filed declaration can reuse the same key after the original is deleted.
CREATE UNIQUE INDEX icp_declarations_organization_id_year_period_uk
    ON iroh.icp_declarations(organization_id, year, period)
    WHERE deleted = FALSE;

-- One version number per (org, year, period) — version 1 is the original,
-- version 2+ are corrections; allows full audit trail of all versions.
CREATE UNIQUE INDEX icp_declarations_organization_id_year_period_version_uk
    ON iroh.icp_declarations(organization_id, year, period, version);

-- =====================================================
-- 4. Create iroh.icp_declaration_lines
-- =====================================================

CREATE TABLE iroh.icp_declaration_lines (
    id               UUID                         NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID                         NOT NULL,
    declaration_id   UUID                         NOT NULL,
    country_code     VARCHAR(2)                   NOT NULL,
    customer_vat_id  VARCHAR(14)                  NOT NULL,
    customer_name    VARCHAR(255),
    supply_type      iroh_icp_supply_type_enum    NOT NULL,
    amount           NUMERIC(15,2)                NOT NULL,
    created_by_id    UUID,
    updated_by_id    UUID,
    created_at       TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    deleted          BOOLEAN                      NOT NULL DEFAULT FALSE,
    CONSTRAINT icp_declaration_lines_pk
        PRIMARY KEY (id),
    CONSTRAINT icp_declaration_lines_declaration_id_fk
        FOREIGN KEY (declaration_id)
        REFERENCES iroh.icp_declarations(id)
        ON DELETE CASCADE,
    CONSTRAINT icp_declaration_lines_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE RESTRICT,
    CONSTRAINT icp_declaration_lines_country_code_not_nl_ck
        CHECK (country_code <> 'NL'),
    CONSTRAINT icp_declaration_lines_country_code_format_ck
        CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT icp_declaration_lines_customer_vat_id_format_ck
        CHECK (customer_vat_id ~ '^[A-Z]{2}[A-Z0-9]{2,12}$'),
    CONSTRAINT icp_declaration_lines_amount_ck
        CHECK (amount > 0)
);

-- No duplicate lines per active declaration: one row per (declaration, country, VAT ID, supply type).
-- Soft-deleted rows are excluded so a corrected line can reuse the same key.
CREATE UNIQUE INDEX icp_declaration_lines_declaration_id_country_vat_supply_uk
    ON iroh.icp_declaration_lines(declaration_id, country_code, customer_vat_id, supply_type)
    WHERE deleted = FALSE;

-- =====================================================
-- 5. Create indexes
-- =====================================================

-- icp_declarations: FK support index
CREATE INDEX icp_declarations_organization_id_idx
    ON iroh.icp_declarations(organization_id);

-- icp_declarations: list view — all active declarations for an org ordered by year desc
CREATE INDEX icp_declarations_organization_id_year_idx
    ON iroh.icp_declarations(organization_id, year DESC)
    WHERE deleted = FALSE;

-- icp_declarations: status-filtered queries (e.g., pending submissions dashboard)
CREATE INDEX icp_declarations_organization_id_status_idx
    ON iroh.icp_declarations(organization_id, status)
    WHERE deleted = FALSE;

-- icp_declaration_lines: FK support index (declaration_id is the primary query key)
CREATE INDEX icp_declaration_lines_declaration_id_idx
    ON iroh.icp_declaration_lines(declaration_id);

-- icp_declaration_lines: org-scoped line queries (denormalized org_id avoids a join)
CREATE INDEX icp_declaration_lines_organization_id_declaration_id_idx
    ON iroh.icp_declaration_lines(organization_id, declaration_id)
    WHERE deleted = FALSE;
