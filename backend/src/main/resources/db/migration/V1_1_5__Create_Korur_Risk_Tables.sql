-- Korur Database Schema - Create Risk Tables
-- V1_2_1__Create_Korur_Risk_Tables.sql
--
-- Phase 1: Creates ISO 27001 risk management tables:
-- risk_assessments, risks, and risk_treatments.
-- The risk_risk_level_enum is shared across risk and supplier assessment domains.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE korur.risk_risk_category_enum AS ENUM (
    'CONFIDENTIALITY',
    'INTEGRITY',
    'AVAILABILITY',
    'COMPLIANCE',
    'OPERATIONAL'
);

CREATE TYPE korur.risk_risk_level_enum AS ENUM (
    'VERY_LOW',
    'LOW',
    'MEDIUM',
    'HIGH',
    'VERY_HIGH'
);

CREATE TYPE korur.risk_risk_status_enum AS ENUM (
    'IDENTIFIED',
    'ASSESSED',
    'TREATING',
    'ACCEPTED',
    'CLOSED'
);

CREATE TYPE korur.risk_assessment_status_enum AS ENUM (
    'PLANNED',
    'IN_PROGRESS',
    'COMPLETED'
);

CREATE TYPE korur.risk_treatment_strategy_enum AS ENUM (
    'MITIGATE',
    'ACCEPT',
    'TRANSFER',
    'AVOID'
);

CREATE TYPE korur.risk_treatment_status_enum AS ENUM (
    'PLANNED',
    'IN_PROGRESS',
    'COMPLETED',
    'OVERDUE'
);

-- =====================================================
-- 2. Create risk_assessments table
-- =====================================================

CREATE TABLE korur.risk_assessments (
    id                     UUID         NOT NULL,
    assessment_code        VARCHAR(50)  NOT NULL,
    organization_id        UUID         NOT NULL,
    assessor_id            UUID         NOT NULL,
    scope                  TEXT         NOT NULL,
    methodology            VARCHAR(500) NOT NULL,
    total_risks_identified INT          NOT NULL,
    critical_risks         INT          NOT NULL,
    high_risks             INT          NOT NULL,
    status                 korur.risk_assessment_status_enum NOT NULL DEFAULT 'PLANNED',
    assessed_at            TIMESTAMP WITH TIME ZONE,
    next_assessment_due    TIMESTAMP WITH TIME ZONE,
    notes                  TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT risk_assessments_pk PRIMARY KEY (id),
    CONSTRAINT risk_assessments_assessor_id_fk FOREIGN KEY (assessor_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 3. Create risks table
-- =====================================================

CREATE TABLE korur.risks (
    id                    UUID         NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    category              korur.risk_risk_category_enum  NOT NULL,
    source                VARCHAR(255),
    asset_reference       VARCHAR(255),
    assessment_id         UUID,
    likelihood            korur.risk_risk_level_enum     NOT NULL,
    impact                korur.risk_risk_level_enum     NOT NULL,
    risk_score            INT          NOT NULL,
    residual_likelihood   korur.risk_risk_level_enum,
    residual_impact       korur.risk_risk_level_enum,
    residual_risk_score   INT,
    status                korur.risk_risk_status_enum    NOT NULL DEFAULT 'IDENTIFIED',
    treatment_strategy    korur.risk_treatment_strategy_enum,
    accepted_by_id        UUID,
    accepted_at           TIMESTAMP WITH TIME ZONE,
    owner_id              UUID         NOT NULL,
    review_date           TIMESTAMP WITH TIME ZONE,
    organization_id       UUID         NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT risks_pk PRIMARY KEY (id),
    CONSTRAINT risks_assessment_id_fk FOREIGN KEY (assessment_id)
        REFERENCES korur.risk_assessments(id),
    CONSTRAINT risks_owner_id_fk FOREIGN KEY (owner_id)
        REFERENCES shrine.users(id),
    CONSTRAINT risks_accepted_by_id_fk FOREIGN KEY (accepted_by_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create risk_treatments table
-- =====================================================

CREATE TABLE korur.risk_treatments (
    id           UUID         NOT NULL,
    risk_id      UUID         NOT NULL,
    action       TEXT         NOT NULL,
    assignee_id  UUID         NOT NULL,
    deadline     TIMESTAMP WITH TIME ZONE,
    status       korur.risk_treatment_status_enum NOT NULL DEFAULT 'PLANNED',
    completed_at TIMESTAMP WITH TIME ZONE,
    notes        TEXT,
    control_id   UUID,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT risk_treatments_pk PRIMARY KEY (id),
    CONSTRAINT risk_treatments_risk_id_fk FOREIGN KEY (risk_id)
        REFERENCES korur.risks(id),
    CONSTRAINT risk_treatments_assignee_id_fk FOREIGN KEY (assignee_id)
        REFERENCES shrine.users(id),
    CONSTRAINT risk_treatments_control_id_fk FOREIGN KEY (control_id)
        REFERENCES korur.controls(id)
);

-- =====================================================
-- 5. Create indexes
-- =====================================================

CREATE INDEX risk_assessments_organization_id_idx
    ON korur.risk_assessments(organization_id);

CREATE INDEX risks_owner_id_idx
    ON korur.risks(owner_id);

CREATE INDEX risks_organization_id_status_idx
    ON korur.risks(organization_id, status);

CREATE INDEX risks_assessment_id_idx
    ON korur.risks(assessment_id);

CREATE INDEX risk_treatments_risk_id_idx
    ON korur.risk_treatments(risk_id);

CREATE INDEX risk_treatments_assignee_id_idx
    ON korur.risk_treatments(assignee_id);

CREATE INDEX risk_treatments_control_id_idx
    ON korur.risk_treatments(control_id);
