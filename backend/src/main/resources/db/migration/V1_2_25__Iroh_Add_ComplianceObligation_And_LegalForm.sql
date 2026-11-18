-- Village Database Schema - Iroh Add ComplianceObligation And LegalForm
-- V1_2_23__Iroh_Add_ComplianceObligation_And_LegalForm.sql
--
-- Adds the VAT deadline warnings foundation to the Iroh finance domain.
--
-- 1) iroh_obligation_type_enum  — which fiscal obligation (VAT return, VPB, etc.)
-- 2) iroh_obligation_status_enum — lifecycle status of a single obligation
-- 3) iroh_legal_form_enum       — legal entity form (NL + EU, covers BV through OY/AB)
-- 4) legal_form column          — added to iroh.organization_settings (nullable)
-- 5) compliance_obligations     — one row per (org, type, period_key); drives the
--                                 scheduler that sends due-date warning emails.
--
-- All enums use native PostgreSQL enum types mapped via Hibernate's
-- PostgreSQLEnumJdbcType. Naming convention: iroh_{enum_class_name_snake_case}_enum

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_obligation_type_enum AS ENUM (
    'VAT_RETURN',
    'VPB_RETURN',
    'IB_RETURN',
    'DEPOSIT_ANNUAL_ACCOUNTS',
    'OTHER'
);

CREATE TYPE iroh_obligation_status_enum AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'SUBMITTED',
    'ACCEPTED',
    'REJECTED',
    'OVERDUE'
);

CREATE TYPE iroh_legal_form_enum AS ENUM (
    'BV',
    'NV',
    'EENMANSZAAK',
    'COOPERATIE',
    'GCV',
    'CVOA',
    'STICHTING',
    'VERENIGING',
    'MAATSCHAP',
    'VOF',
    'CV',
    'OY',
    'AB'
);

-- =====================================================
-- 2. Extend iroh.organization_settings
-- =====================================================

-- Nullable — existing rows have no legal form set; backend service populates on update.
ALTER TABLE iroh.organization_settings
    ADD COLUMN legal_form VARCHAR(50) NULL;

-- =====================================================
-- 3. Create compliance_obligations table
-- =====================================================

CREATE TABLE iroh.compliance_obligations (
    id                   UUID                         NOT NULL DEFAULT gen_random_uuid(),
    organization_id      UUID                         NOT NULL,
    type                 iroh_obligation_type_enum    NOT NULL,
    status               iroh_obligation_status_enum  NOT NULL DEFAULT 'OPEN',
    period_key           VARCHAR(50)                  NOT NULL,
    due_date             DATE                         NOT NULL,
    warned_at            TIMESTAMP WITH TIME ZONE,
    submitted_at         TIMESTAMP WITH TIME ZONE,
    submitted_reference  VARCHAR(500),
    created_at           TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    created_by_id        UUID,
    updated_by_id        UUID,
    CONSTRAINT compliance_obligations_pk PRIMARY KEY (id),
    CONSTRAINT compliance_obligations_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT compliance_obligations_organization_id_period_key_type_uk
        UNIQUE (organization_id, period_key, type),
    CONSTRAINT compliance_obligations_due_date_ck
        CHECK (due_date >= created_at::date)
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- FK support
CREATE INDEX compliance_obligations_organization_id_idx
    ON iroh.compliance_obligations(organization_id);

-- Scheduler query: "all obligations of a given type for an org"
CREATE INDEX compliance_obligations_organization_id_type_idx
    ON iroh.compliance_obligations(organization_id, type);

-- Scheduler query: "obligations due within the next 14 days for an org"
CREATE INDEX compliance_obligations_organization_id_due_date_idx
    ON iroh.compliance_obligations(organization_id, due_date);

-- Scheduler query: "open obligations not yet warned" — filters on status + warned_at IS NULL
CREATE INDEX compliance_obligations_organization_id_status_warned_at_idx
    ON iroh.compliance_obligations(organization_id, status, warned_at);
