-- Korur Database Schema - Create Audit and Management Review Tables
-- V1_2_3__Create_Korur_Audit_And_Review_Tables.sql
--
-- Phase 3: Creates ISO 27001 audit, management review, and improvement tables:
-- audits, audit_findings, corrective_actions, management_reviews,
-- management_review_actions, and improvements.
-- The audit_action_status_enum is shared between corrective_actions
-- and management_review_actions.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE korur.audit_audit_type_enum AS ENUM (
    'INTERNAL',
    'EXTERNAL'
);

CREATE TYPE korur.audit_audit_status_enum AS ENUM (
    'PLANNED',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED'
);

CREATE TYPE korur.audit_finding_severity_enum AS ENUM (
    'MAJOR_NONCONFORMITY',
    'MINOR_NONCONFORMITY',
    'OBSERVATION',
    'POSITIVE'
);

CREATE TYPE korur.audit_finding_status_enum AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'RESOLVED',
    'CLOSED'
);

CREATE TYPE korur.audit_action_status_enum AS ENUM (
    'PLANNED',
    'IN_PROGRESS',
    'COMPLETED',
    'VERIFIED'
);

CREATE TYPE korur.audit_improvement_source_enum AS ENUM (
    'AUDIT',
    'INCIDENT',
    'MANAGEMENT_REVIEW',
    'RISK_REVIEW',
    'SUGGESTION',
    'TREND_ANALYSIS'
);

CREATE TYPE korur.audit_improvement_status_enum AS ENUM (
    'PROPOSED',
    'PENDING_APPROVAL',
    'IN_PROGRESS',
    'COMPLETED'
);

-- =====================================================
-- 2. Create audits table
-- =====================================================

CREATE TABLE korur.audits (
    id               UUID         NOT NULL,
    title            VARCHAR(255) NOT NULL,
    audit_code       VARCHAR(50)  NOT NULL,
    scope            TEXT         NOT NULL,
    audit_type       korur.audit_audit_type_enum   NOT NULL,
    status           korur.audit_audit_status_enum NOT NULL DEFAULT 'PLANNED',
    lead_auditor_id  UUID         NOT NULL,
    scheduled_start  TIMESTAMP WITH TIME ZONE,
    scheduled_end    TIMESTAMP WITH TIME ZONE,
    completed_at     TIMESTAMP WITH TIME ZONE,
    summary          TEXT,
    organization_id  UUID         NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audits_pk PRIMARY KEY (id),
    CONSTRAINT audits_lead_auditor_id_fk FOREIGN KEY (lead_auditor_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 3. Create audit_findings table
-- =====================================================

CREATE TABLE korur.audit_findings (
    id            UUID         NOT NULL,
    audit_id      UUID         NOT NULL,
    finding_code  VARCHAR(50)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    severity      korur.audit_finding_severity_enum NOT NULL,
    control_id    UUID,
    status        korur.audit_finding_status_enum   NOT NULL DEFAULT 'OPEN',
    due_date      TIMESTAMP WITH TIME ZONE,
    resolved_at   TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_findings_pk PRIMARY KEY (id),
    CONSTRAINT audit_findings_audit_id_fk FOREIGN KEY (audit_id)
        REFERENCES korur.audits(id),
    CONSTRAINT audit_findings_control_id_fk FOREIGN KEY (control_id)
        REFERENCES korur.controls(id)
);

-- =====================================================
-- 4. Create corrective_actions table
-- =====================================================

CREATE TABLE korur.corrective_actions (
    id                   UUID         NOT NULL,
    finding_id           UUID         NOT NULL,
    action               TEXT         NOT NULL,
    assignee_id          UUID         NOT NULL,
    status               korur.audit_action_status_enum NOT NULL DEFAULT 'PLANNED',
    deadline             TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at         TIMESTAMP WITH TIME ZONE,
    verified_at          TIMESTAMP WITH TIME ZONE,
    verified_by_id       UUID,
    effectiveness_review TEXT,
    notes                TEXT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT corrective_actions_pk PRIMARY KEY (id),
    CONSTRAINT corrective_actions_finding_id_fk FOREIGN KEY (finding_id)
        REFERENCES korur.audit_findings(id),
    CONSTRAINT corrective_actions_assignee_id_fk FOREIGN KEY (assignee_id)
        REFERENCES shrine.users(id),
    CONSTRAINT corrective_actions_verified_by_id_fk FOREIGN KEY (verified_by_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 5. Create management_reviews table
-- =====================================================

CREATE TABLE korur.management_reviews (
    id                         UUID         NOT NULL,
    review_code                VARCHAR(50)  NOT NULL,
    review_date                TIMESTAMP WITH TIME ZONE NOT NULL,
    chair_person_id            UUID         NOT NULL,
    attendees                  TEXT         NOT NULL,
    previous_action_status     TEXT,
    isms_changes               TEXT,
    audit_results              TEXT,
    risk_status                TEXT,
    performance_metrics        TEXT,
    objective_progress         TEXT,
    interested_party_feedback  TEXT,
    decisions                  TEXT         NOT NULL,
    improvement_actions        TEXT,
    resource_needs             TEXT,
    next_review_date           TIMESTAMP WITH TIME ZONE,
    organization_id            UUID         NOT NULL,
    deleted                    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT management_reviews_pk PRIMARY KEY (id),
    CONSTRAINT management_reviews_chair_person_id_fk FOREIGN KEY (chair_person_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 6. Create management_review_actions table
-- =====================================================

CREATE TABLE korur.management_review_actions (
    id           UUID         NOT NULL,
    review_id    UUID         NOT NULL,
    action       TEXT         NOT NULL,
    assignee_id  UUID         NOT NULL,
    deadline     TIMESTAMP WITH TIME ZONE NOT NULL,
    status       korur.audit_action_status_enum NOT NULL DEFAULT 'PLANNED',
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT management_review_actions_pk PRIMARY KEY (id),
    CONSTRAINT management_review_actions_review_id_fk FOREIGN KEY (review_id)
        REFERENCES korur.management_reviews(id),
    CONSTRAINT management_review_actions_assignee_id_fk FOREIGN KEY (assignee_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 7. Create improvements table
-- =====================================================

CREATE TABLE korur.improvements (
    id                   UUID          NOT NULL,
    title                VARCHAR(255)  NOT NULL,
    description          TEXT          NOT NULL,
    source               korur.audit_improvement_source_enum NOT NULL,
    source_reference_id  UUID,
    status               korur.audit_improvement_status_enum NOT NULL DEFAULT 'PROPOSED',
    expected_benefit     VARCHAR(1000),
    actual_outcome       TEXT,
    assignee_id          UUID,
    deadline             TIMESTAMP WITH TIME ZONE,
    completed_at         TIMESTAMP WITH TIME ZONE,
    organization_id      UUID          NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT improvements_pk PRIMARY KEY (id),
    CONSTRAINT improvements_assignee_id_fk FOREIGN KEY (assignee_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 8. Create indexes
-- =====================================================

CREATE INDEX audits_organization_id_status_idx
    ON korur.audits(organization_id, status);

CREATE INDEX audit_findings_audit_id_idx
    ON korur.audit_findings(audit_id);

CREATE INDEX audit_findings_control_id_idx
    ON korur.audit_findings(control_id);

CREATE INDEX audit_findings_status_idx
    ON korur.audit_findings(status);

CREATE INDEX corrective_actions_finding_id_idx
    ON korur.corrective_actions(finding_id);

CREATE INDEX corrective_actions_assignee_id_idx
    ON korur.corrective_actions(assignee_id);

CREATE INDEX management_reviews_organization_id_idx
    ON korur.management_reviews(organization_id);

CREATE INDEX management_review_actions_review_id_idx
    ON korur.management_review_actions(review_id);

CREATE INDEX improvements_organization_id_status_idx
    ON korur.improvements(organization_id, status);
