-- Korur Database Schema - Create Policy Tables
-- V1_1_7__Create_Korur_Policy_Tables.sql
--
-- Phase 2: Creates ISO 27001 policy management tables:
-- policies, policy_versions, and policy_control_mappings.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE korur.policy_policy_category_enum AS ENUM (
    'INFORMATION_SECURITY',
    'ACCESS_CONTROL',
    'ACCEPTABLE_USE',
    'INCIDENT_RESPONSE',
    'BCP',
    'DATA_CLASSIFICATION',
    'SUPPLIER',
    'PHYSICAL',
    'OTHER'
);

CREATE TYPE korur.policy_policy_status_enum AS ENUM (
    'DRAFT',
    'PENDING_APPROVAL',
    'ARCHIVED'
);

-- =====================================================
-- 2. Create policies table
-- =====================================================

CREATE TABLE korur.policies (
    id               UUID         NOT NULL,
    title            VARCHAR(255) NOT NULL,
    policy_code      VARCHAR(50)  NOT NULL,
    category         korur.policy_policy_category_enum NOT NULL DEFAULT 'INFORMATION_SECURITY',
    content          TEXT,
    document_url     VARCHAR(512),
    version          INT          NOT NULL DEFAULT 1,
    status           korur.policy_policy_status_enum   NOT NULL DEFAULT 'DRAFT',
    effective_from   TIMESTAMP WITH TIME ZONE,
    next_review_date TIMESTAMP WITH TIME ZONE,
    owner_id         UUID         NOT NULL,
    organization_id  UUID         NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT policies_pk PRIMARY KEY (id),
    CONSTRAINT policies_owner_id_fk FOREIGN KEY (owner_id)
        REFERENCES shrine.users(id),
    CONSTRAINT policies_policy_code_organization_id_uk
        UNIQUE (policy_code, organization_id)
);

-- =====================================================
-- 3. Create policy_versions table
-- =====================================================

CREATE TABLE korur.policy_versions (
    id               UUID          NOT NULL,
    policy_id        UUID          NOT NULL,
    version_number   INT           NOT NULL,
    change_reason    VARCHAR(1000) NOT NULL,
    content_snapshot TEXT,
    changed_by_id    UUID          NOT NULL,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT policy_versions_pk PRIMARY KEY (id),
    CONSTRAINT policy_versions_policy_id_fk FOREIGN KEY (policy_id)
        REFERENCES korur.policies(id),
    CONSTRAINT policy_versions_changed_by_id_fk FOREIGN KEY (changed_by_id)
        REFERENCES shrine.users(id),
    CONSTRAINT policy_versions_policy_id_version_number_uk
        UNIQUE (policy_id, version_number)
);

-- =====================================================
-- 4. Create policy_control_mappings table
-- =====================================================

CREATE TABLE korur.policy_control_mappings (
    id              UUID          NOT NULL,
    policy_id       UUID          NOT NULL,
    control_id      UUID          NOT NULL,
    justification   VARCHAR(1000),
    mapped_by_id    UUID          NOT NULL,
    organization_id UUID          NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT policy_control_mappings_pk PRIMARY KEY (id),
    CONSTRAINT policy_control_mappings_policy_id_fk FOREIGN KEY (policy_id)
        REFERENCES korur.policies(id) ON DELETE CASCADE,
    CONSTRAINT policy_control_mappings_control_id_fk FOREIGN KEY (control_id)
        REFERENCES korur.controls(id),
    CONSTRAINT policy_control_mappings_mapped_by_id_fk FOREIGN KEY (mapped_by_id)
        REFERENCES shrine.users(id),
    CONSTRAINT policy_control_mappings_policy_id_control_id_uk
        UNIQUE (policy_id, control_id)
);

-- =====================================================
-- 5. Create indexes
-- =====================================================

CREATE INDEX policies_organization_id_status_idx
    ON korur.policies(organization_id, status);

CREATE INDEX policy_versions_policy_id_idx
    ON korur.policy_versions(policy_id);

CREATE INDEX policy_control_mappings_policy_id_idx
    ON korur.policy_control_mappings(policy_id);

CREATE INDEX policy_control_mappings_control_id_idx
    ON korur.policy_control_mappings(control_id);

CREATE INDEX policy_control_mappings_organization_id_idx
    ON korur.policy_control_mappings(organization_id);
