-- Korur Database Schema - Create Business Continuity Planning Tables
-- V1_2_6__Create_Korur_BCP_Tables.sql
--
-- Phase 6: Creates ISO 27001 business continuity planning tables:
-- continuity_plans, recovery_objectives, and continuity_tests.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE korur.bcp_plan_type_enum AS ENUM (
    'BCP',
    'DRP',
    'IRP'
);

CREATE TYPE korur.bcp_plan_status_enum AS ENUM (
    'DRAFT',
    'PENDING_APPROVAL',
    'ACTIVE',
    'ARCHIVED'
);

CREATE TYPE korur.bcp_test_type_enum AS ENUM (
    'TABLETOP',
    'WALKTHROUGH',
    'SIMULATION',
    'FULL_EXERCISE'
);

CREATE TYPE korur.bcp_test_result_enum AS ENUM (
    'PASSED',
    'PARTIAL_PASS',
    'FAILED'
);

-- =====================================================
-- 2. Create continuity_plans table
-- =====================================================

CREATE TABLE korur.continuity_plans (
    id               UUID         NOT NULL,
    title            VARCHAR(255) NOT NULL,
    plan_code        VARCHAR(50)  NOT NULL,
    plan_type        korur.bcp_plan_type_enum   NOT NULL,
    scope            TEXT         NOT NULL,
    content          TEXT,
    document_url     VARCHAR(512),
    version          INT          NOT NULL DEFAULT 1,
    status           korur.bcp_plan_status_enum NOT NULL DEFAULT 'DRAFT',
    next_review_date TIMESTAMP WITH TIME ZONE,
    last_tested_at   TIMESTAMP WITH TIME ZONE,
    owner_id         UUID         NOT NULL,
    organization_id  UUID         NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT continuity_plans_pk PRIMARY KEY (id),
    CONSTRAINT continuity_plans_owner_id_fk FOREIGN KEY (owner_id)
        REFERENCES shrine.users(id),
    CONSTRAINT continuity_plans_plan_code_organization_id_uk
        UNIQUE (plan_code, organization_id)
);

-- =====================================================
-- 3. Create recovery_objectives table
-- =====================================================

CREATE TABLE korur.recovery_objectives (
    id           UUID          NOT NULL,
    plan_id      UUID          NOT NULL,
    service_name VARCHAR(255)  NOT NULL,
    rpo_minutes  INT           NOT NULL,
    rto_minutes  INT           NOT NULL,
    priority     INT           NOT NULL,
    dependencies VARCHAR(1000),
    deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT recovery_objectives_pk PRIMARY KEY (id),
    CONSTRAINT recovery_objectives_plan_id_fk FOREIGN KEY (plan_id)
        REFERENCES korur.continuity_plans(id)
);

-- =====================================================
-- 4. Create continuity_tests table
-- =====================================================

CREATE TABLE korur.continuity_tests (
    id                 UUID          NOT NULL,
    plan_id            UUID          NOT NULL,
    test_type          korur.bcp_test_type_enum   NOT NULL,
    tested_at          TIMESTAMP WITH TIME ZONE   NOT NULL,
    participants       VARCHAR(1000),
    result             korur.bcp_test_result_enum NOT NULL,
    actual_rpo_minutes INT,
    actual_rto_minutes INT,
    findings           TEXT,
    improvements       TEXT,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT continuity_tests_pk PRIMARY KEY (id),
    CONSTRAINT continuity_tests_plan_id_fk FOREIGN KEY (plan_id)
        REFERENCES korur.continuity_plans(id)
);

-- =====================================================
-- 5. Create indexes
-- =====================================================

CREATE INDEX continuity_plans_organization_id_idx
    ON korur.continuity_plans(organization_id);

CREATE INDEX recovery_objectives_plan_id_idx
    ON korur.recovery_objectives(plan_id);

CREATE INDEX continuity_tests_plan_id_idx
    ON korur.continuity_tests(plan_id);
