-- Village Database Schema - Create Access Visibility & Review Tables
-- V1_1_11__Create_Access_Tables.sql
--
-- Introduces the RBAC domain for ISO 27001 A.5.15-A.5.18 access visibility,
-- external connector management, and periodic access reviews.
-- data_classification_enum already exists in korur schema — NOT recreated here.

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE korur.rbac_source_type_enum AS ENUM (
    'VILLAGE', 'KEYCLOAK', 'AZURE', 'POSTGRESQL', 'MANUAL'
);

CREATE TYPE korur.rbac_sync_status_enum AS ENUM (
    'NEVER_SYNCED', 'SUCCESS', 'FAILED', 'IN_PROGRESS'
);

CREATE TYPE korur.rbac_identity_type_enum AS ENUM (
    'USER', 'SERVICE_ACCOUNT', 'GROUP', 'ROLE'
);

CREATE TYPE korur.rbac_resource_type_enum AS ENUM (
    'SERVER', 'DATABASE', 'APPLICATION', 'CLOUD_RESOURCE', 'SUBSCRIPTION',
    'DEVICE', 'REPOSITORY', 'NETWORK', 'OTHER'
);

CREATE TYPE korur.rbac_access_level_enum AS ENUM (
    'ADMIN', 'WRITE', 'READ', 'CUSTOM'
);

CREATE TYPE korur.rbac_review_decision_enum AS ENUM (
    'PENDING', 'APPROVED', 'REVOKED', 'FLAGGED'
);

CREATE TYPE korur.rbac_review_type_enum AS ENUM (
    'QUARTERLY', 'ANNUAL', 'AD_HOC', 'PRIVILEGED'
);

CREATE TYPE korur.rbac_review_status_enum AS ENUM (
    'DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
);

-- =====================================================
-- 2. Create access_sources table
-- =====================================================

CREATE TABLE korur.access_sources (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                 VARCHAR(255) NOT NULL,
    source_type          korur.rbac_source_type_enum NOT NULL,
    connection_config    TEXT,
    last_sync_at         TIMESTAMPTZ,
    sync_status          korur.rbac_sync_status_enum NOT NULL DEFAULT 'NEVER_SYNCED',
    sync_interval_hours  INTEGER      NOT NULL DEFAULT 24,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                TEXT,
    organization_id      UUID         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT access_sources_pk PRIMARY KEY (id)
);

-- =====================================================
-- 3. Create access_rights table
-- =====================================================

CREATE TABLE korur.access_rights (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id               UUID,
    external_identity     VARCHAR(255),
    identity_type         korur.rbac_identity_type_enum NOT NULL,
    resource_type         korur.rbac_resource_type_enum NOT NULL,
    resource_name         VARCHAR(255) NOT NULL,
    resource_detail       VARCHAR(500),
    access_level          korur.rbac_access_level_enum NOT NULL,
    access_detail         VARCHAR(500),
    privileged            BOOLEAN      NOT NULL DEFAULT FALSE,
    source_id             UUID,
    classification        korur.data_classification_enum,
    granted_at            TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ,
    last_reviewed_at      TIMESTAMPTZ,
    last_review_decision  korur.rbac_review_decision_enum,
    reviewed_by_user_id   UUID,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    organization_id       UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT access_rights_pk PRIMARY KEY (id),
    CONSTRAINT access_rights_source_id_fk FOREIGN KEY (source_id) REFERENCES korur.access_sources(id),
    CONSTRAINT access_rights_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id),
    CONSTRAINT access_rights_reviewed_by_user_id_fk FOREIGN KEY (reviewed_by_user_id) REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create access_reviews table
-- =====================================================

CREATE TABLE korur.access_reviews (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    review_type       korur.rbac_review_type_enum NOT NULL,
    status            korur.rbac_review_status_enum NOT NULL DEFAULT 'DRAFT',
    due_date          TIMESTAMPTZ  NOT NULL,
    completed_at      TIMESTAMPTZ,
    reviewer_user_id  UUID         NOT NULL,
    policy_id         UUID,
    notes             TEXT,
    organization_id   UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT access_reviews_pk PRIMARY KEY (id),
    CONSTRAINT access_reviews_reviewer_user_id_fk FOREIGN KEY (reviewer_user_id) REFERENCES shrine.users(id),
    CONSTRAINT access_reviews_policy_id_fk FOREIGN KEY (policy_id) REFERENCES korur.policies(id)
);

-- =====================================================
-- 5. Create access_review_items table
-- =====================================================

CREATE TABLE korur.access_review_items (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    review_id              UUID         NOT NULL,
    access_right_id        UUID         NOT NULL,
    snapshot_identity      VARCHAR(255) NOT NULL,
    snapshot_resource      VARCHAR(255) NOT NULL,
    snapshot_access_level  VARCHAR(255) NOT NULL,
    snapshot_privileged    BOOLEAN      NOT NULL,
    decision               korur.rbac_review_decision_enum NOT NULL DEFAULT 'PENDING',
    justification          TEXT,
    decided_at             TIMESTAMPTZ,
    decided_by_user_id     UUID,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    organization_id        UUID         NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT access_review_items_pk PRIMARY KEY (id),
    CONSTRAINT access_review_items_review_id_fk FOREIGN KEY (review_id) REFERENCES korur.access_reviews(id) ON DELETE CASCADE,
    CONSTRAINT access_review_items_access_right_id_fk FOREIGN KEY (access_right_id) REFERENCES korur.access_rights(id),
    CONSTRAINT access_review_items_decided_by_user_id_fk FOREIGN KEY (decided_by_user_id) REFERENCES shrine.users(id)
);

-- =====================================================
-- 6. Create indexes
-- =====================================================

-- access_sources indexes
CREATE INDEX access_sources_organization_id_idx ON korur.access_sources(organization_id);
CREATE INDEX access_sources_source_type_idx ON korur.access_sources(source_type);

-- access_rights indexes
CREATE INDEX access_rights_organization_id_idx ON korur.access_rights(organization_id);
CREATE INDEX access_rights_source_id_idx ON korur.access_rights(source_id);
CREATE INDEX access_rights_user_id_idx ON korur.access_rights(user_id);
CREATE INDEX access_rights_resource_type_idx ON korur.access_rights(resource_type);
CREATE INDEX access_rights_access_level_idx ON korur.access_rights(access_level);
CREATE INDEX access_rights_expires_at_idx ON korur.access_rights(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX access_rights_last_reviewed_at_idx ON korur.access_rights(last_reviewed_at);
CREATE INDEX access_rights_privileged_idx ON korur.access_rights(privileged) WHERE privileged = TRUE;

-- access_reviews indexes
CREATE INDEX access_reviews_organization_id_idx ON korur.access_reviews(organization_id);
CREATE INDEX access_reviews_status_idx ON korur.access_reviews(status);
CREATE INDEX access_reviews_reviewer_user_id_idx ON korur.access_reviews(reviewer_user_id);

-- access_review_items indexes
CREATE INDEX access_review_items_review_id_idx ON korur.access_review_items(review_id);
CREATE INDEX access_review_items_access_right_id_idx ON korur.access_review_items(access_right_id);
CREATE INDEX access_review_items_organization_id_idx ON korur.access_review_items(organization_id);
CREATE INDEX access_review_items_decision_idx ON korur.access_review_items(decision);
