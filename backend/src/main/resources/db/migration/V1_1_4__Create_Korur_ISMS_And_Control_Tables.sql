-- Korur Database Schema - Create ISMS and Control Tables
-- V1_2_0__Create_Korur_ISMS_And_Control_Tables.sql
--
-- Phase 0: Establishes the korur schema and creates the foundational
-- ISO 27001 ISMS tables: isms_scopes, security_objectives, controls,
-- control_evidences, and compliance_snapshots.

-- =====================================================
-- 0. Create korur schema
-- =====================================================

CREATE SCHEMA IF NOT EXISTS korur;

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE korur.control_control_category_enum AS ENUM (
    'ORGANIZATIONAL',
    'PEOPLE',
    'PHYSICAL',
    'TECHNOLOGICAL'
);

CREATE TYPE korur.control_control_type_enum AS ENUM (
    'PREVENTIVE',
    'DETECTIVE',
    'CORRECTIVE'
);

CREATE TYPE korur.control_implementation_status_enum AS ENUM (
    'NOT_IMPLEMENTED',
    'PARTIALLY_IMPLEMENTED',
    'FULLY_IMPLEMENTED',
    'NOT_APPLICABLE'
);

CREATE TYPE korur.control_evidence_type_enum AS ENUM (
    'DOCUMENT',
    'SCAN_RESULT',
    'POLICY',
    'AUDIT_FINDING',
    'SCREENSHOT',
    'LOG',
    'OTHER'
);

CREATE TYPE korur.control_objective_status_enum AS ENUM (
    'ON_TRACK',
    'AT_RISK',
    'OFF_TRACK',
    'ACHIEVED'
);

CREATE TYPE korur.control_snapshot_trigger_enum AS ENUM (
    'MANUAL',
    'SCHEDULED',
    'CONTROL_UPDATE'
);

CREATE TYPE korur.data_classification_enum AS ENUM (
    'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'
);

-- =====================================================
-- 2. Create isms_scopes table
-- =====================================================

CREATE TABLE korur.isms_scopes (
    id                        UUID         NOT NULL,
    scope_statement           TEXT         NOT NULL,
    boundaries                TEXT,
    exclusions                TEXT,
    internal_issues           TEXT,
    external_issues           TEXT,
    interested_parties        TEXT,
    risk_acceptance_threshold INT          NOT NULL,
    risk_acceptance_criteria  TEXT,
    version                   INT          NOT NULL DEFAULT 1,
    effective_from            TIMESTAMP WITH TIME ZONE,
    organization_id           UUID         NOT NULL,
    deleted                   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT isms_scopes_pk PRIMARY KEY (id),
    CONSTRAINT isms_scopes_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT isms_scopes_organization_id_uk UNIQUE (organization_id)
);

-- =====================================================
-- 3. Create security_objectives table
-- =====================================================

CREATE TABLE korur.security_objectives (
    id              UUID         NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    metric          VARCHAR(500) NOT NULL,
    target_value    VARCHAR(255) NOT NULL,
    current_value   VARCHAR(255),
    status          korur.control_objective_status_enum NOT NULL DEFAULT 'ON_TRACK',
    responsible_id  UUID         NOT NULL,
    period          VARCHAR(50)  NOT NULL,
    review_date     TIMESTAMP WITH TIME ZONE,
    organization_id UUID         NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT security_objectives_pk PRIMARY KEY (id),
    CONSTRAINT security_objectives_responsible_id_fk FOREIGN KEY (responsible_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create controls table
-- =====================================================

CREATE TABLE korur.controls (
    id                    UUID         NOT NULL,
    control_code          VARCHAR(20)  NOT NULL,
    category              korur.control_control_category_enum  NOT NULL,
    control_type          korur.control_control_type_enum      NOT NULL,
    theme                 VARCHAR(100) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    applicable            BOOLEAN      NOT NULL DEFAULT TRUE,
    justification         VARCHAR(1000),
    implementation_status korur.control_implementation_status_enum NOT NULL DEFAULT 'NOT_IMPLEMENTED',
    organization_id       UUID         NOT NULL,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT controls_pk PRIMARY KEY (id),
    CONSTRAINT controls_control_code_organization_id_uk UNIQUE (control_code, organization_id)
);

-- =====================================================
-- 5. Create control_evidences table
-- =====================================================

CREATE TABLE korur.control_evidences (
    id              UUID         NOT NULL,
    control_id      UUID         NOT NULL,
    evidence_type   korur.control_evidence_type_enum NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     VARCHAR(1000),
    source_url      VARCHAR(512),
    source_domain   VARCHAR(100),
    collected_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until     TIMESTAMP WITH TIME ZONE,
    organization_id UUID         NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT control_evidences_pk PRIMARY KEY (id),
    CONSTRAINT control_evidences_control_id_fk FOREIGN KEY (control_id)
        REFERENCES korur.controls(id)
);

-- =====================================================
-- 6. Create compliance_snapshots table
-- =====================================================

CREATE TABLE korur.compliance_snapshots (
    id                       UUID         NOT NULL,
    organization_id          UUID         NOT NULL,
    total_controls           INT          NOT NULL,
    fully_implemented        INT          NOT NULL,
    partially_implemented    INT          NOT NULL,
    not_implemented          INT          NOT NULL,
    compliance_percentage    DECIMAL(5,2) NOT NULL,
    triggered_by             korur.control_snapshot_trigger_enum NOT NULL,
    snapshot_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT compliance_snapshots_pk PRIMARY KEY (id),
    CONSTRAINT compliance_snapshots_organization_id_snapshot_at_uk
        UNIQUE (organization_id, snapshot_at)
);

-- =====================================================
-- 7. Create indexes
-- =====================================================

CREATE INDEX security_objectives_organization_id_period_idx
    ON korur.security_objectives(organization_id, period);

CREATE INDEX controls_organization_id_idx
    ON korur.controls(organization_id);

CREATE INDEX control_evidences_control_id_idx
    ON korur.control_evidences(control_id);

CREATE INDEX control_evidences_organization_id_idx
    ON korur.control_evidences(organization_id);

CREATE INDEX compliance_snapshots_organization_id_idx
    ON korur.compliance_snapshots(organization_id);
