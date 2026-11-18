-- Village Database Schema - Iroh Add Fiscal Year Data
-- V1_2_30__Iroh_Add_Fiscal_Year_Data.sql
--
-- Introduces the iroh.fiscal_year_data table for storing per-organization,
-- per-year fiscal adjustments used by the Winst & Verlies (P&L) reporting feature.
--
-- Dutch accounting requires a set of manual overrides on top of the commercially
-- computed profit to arrive at the taxable (fiscal) profit:
--   - Fiscale correcties: general adjustments to commercial profit
--   - Investeringsaftrek: KIA (Kleinschaligheidsinvesteringsaftrek), MIA
--     (Milieu-Investeringsaftrek), and VAMIL (Vrije Afschrijving Milieu-Investeringen)
--   - Niet-aftrekbare kosten: costs disallowed as a tax deduction
--   - Verliesverrekening: carry-forward and carry-back of prior-year losses
--
-- Each row is uniquely scoped to one (organization_id, fiscal_year) pair so the
-- application can do a simple SELECT … WHERE organization_id = ? AND fiscal_year = ?
-- lookup and an upsert on that same key.
--
-- All numeric amounts are NUMERIC(19,2) — matching the precision used for monetary
-- values throughout the Iroh domain — and default to 0 so rows can be inserted with
-- only the fields the user has touched.
--
-- Cascade: when the parent shrine.organizations row is deleted all fiscal data for
-- that organization is also deleted (ON DELETE CASCADE), consistent with every other
-- Iroh table that holds per-organization data.

-- =====================================================
-- 1. Create iroh.fiscal_year_data
-- =====================================================

CREATE TABLE iroh.fiscal_year_data (
    id                          UUID            NOT NULL DEFAULT gen_random_uuid(),
    organization_id             UUID            NOT NULL,
    fiscal_year                 INTEGER         NOT NULL,
    -- Fiscale correcties
    fiscal_correction_amount    NUMERIC(19,2)   NOT NULL DEFAULT 0,
    fiscal_correction_notes     TEXT,
    -- Investeringsaftrek
    kia_amount                  NUMERIC(19,2)   NOT NULL DEFAULT 0,
    mia_amount                  NUMERIC(19,2)   NOT NULL DEFAULT 0,
    vamil_amount                NUMERIC(19,2)   NOT NULL DEFAULT 0,
    -- Niet-aftrekbare kosten
    non_deductible_amount       NUMERIC(19,2)   NOT NULL DEFAULT 0,
    -- Verliesverrekening
    loss_carryforward_amount    NUMERIC(19,2)   NOT NULL DEFAULT 0,
    loss_carryback_amount       NUMERIC(19,2)   NOT NULL DEFAULT 0,
    -- Audit timestamps
    created_at                  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fiscal_year_data_pk
        PRIMARY KEY (id),
    CONSTRAINT fiscal_year_data_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE CASCADE,
    CONSTRAINT fiscal_year_data_organization_id_fiscal_year_uk
        UNIQUE (organization_id, fiscal_year)
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- FK support and primary partition key for all application queries.
CREATE INDEX fiscal_year_data_organization_id_idx
    ON iroh.fiscal_year_data(organization_id);
