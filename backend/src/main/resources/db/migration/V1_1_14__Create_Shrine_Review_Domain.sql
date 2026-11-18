-- Village Database Schema - Create Shrine Review Domain
-- V1_1_17__Create_Shrine_Review_Domain.sql
--
-- Creates the shared approval and acknowledgement tables in the shrine schema.
-- These tables centralize the approval workflow used by Policy, BCP, ISMS Scope,
-- Improvement, and Patch Request domains.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE shrine.approval_status_enum AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED',
    'WITHDRAWN'
);

CREATE TYPE shrine.approval_decision_enum AS ENUM (
    'APPROVED',
    'REJECTED'
);

CREATE TYPE shrine.approval_subject_type_enum AS ENUM (
    'POLICY',
    'BCP_PLAN',
    'ISMS_SCOPE',
    'IMPROVEMENT',
    'PATCH_REQUEST'
);

-- =====================================================
-- 2. Create approvals table
-- =====================================================

CREATE TABLE shrine.approvals (
    id               UUID         NOT NULL,
    subject_type     shrine.approval_subject_type_enum NOT NULL,
    subject_id       UUID         NOT NULL,
    status           shrine.approval_status_enum NOT NULL DEFAULT 'PENDING',
    requested_by_id  UUID         NOT NULL,
    assignee_id      UUID         NOT NULL,
    request_comment  VARCHAR(2000),
    decision         shrine.approval_decision_enum,
    decision_comment VARCHAR(2000),
    decided_at       TIMESTAMP WITH TIME ZONE,
    organization_id  UUID         NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT approvals_pk PRIMARY KEY (id)
);

-- =====================================================
-- 3. Create acknowledgements table
-- =====================================================

CREATE TABLE shrine.acknowledgements (
    id               UUID    NOT NULL,
    subject_type     shrine.approval_subject_type_enum NOT NULL,
    subject_id       UUID    NOT NULL,
    user_id          UUID    NOT NULL,
    document_version INT     NOT NULL,
    acknowledged_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_id  UUID    NOT NULL,
    deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT acknowledgements_pk PRIMARY KEY (id),
    CONSTRAINT acknowledgements_subject_user_version_uk
        UNIQUE (subject_id, user_id, document_version)
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

CREATE INDEX approvals_org_subject_idx
    ON shrine.approvals(organization_id, subject_type, subject_id);

CREATE INDEX approvals_subject_status_idx
    ON shrine.approvals(subject_id, status);

CREATE INDEX approvals_assignee_status_idx
    ON shrine.approvals(assignee_id, status);

CREATE INDEX acknowledgements_subject_id_idx
    ON shrine.acknowledgements(subject_id);

CREATE INDEX acknowledgements_user_subject_idx
    ON shrine.acknowledgements(user_id, subject_type);
