-- Korur Database Schema - Create Asset Tables
-- V1_1_16__Create_Korur_Asset_Tables.sql
--
-- Introduces the asset domain for tracking physical and infrastructure
-- IT assets, ownership, classification, and custody assignments
-- (ISO 27001 A.5.9-A.5.14).

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE korur.asset_asset_type_enum AS ENUM (
    'HARDWARE', 'NETWORK', 'DATA', 'FACILITY', 'OTHER'
);

CREATE TYPE korur.asset_asset_status_enum AS ENUM (
    'ACTIVE', 'MAINTENANCE', 'RETIRED', 'LOST', 'STOLEN'
);

CREATE TYPE korur.asset_assignment_status_enum AS ENUM (
    'ACTIVE', 'RETURNED', 'TRANSFERRED'
);

-- =====================================================
-- 2. Create assets table
-- =====================================================

CREATE TABLE korur.assets (
    id                  UUID        NOT NULL,
    organization_id     UUID        NOT NULL,
    name                VARCHAR(255) NOT NULL,
    asset_tag           VARCHAR(100) NOT NULL,
    description         TEXT,
    asset_type          korur.asset_asset_type_enum NOT NULL,
    status              korur.asset_asset_status_enum NOT NULL DEFAULT 'ACTIVE',
    classification      korur.data_classification_enum NOT NULL,
    owner_user_id       UUID,
    assigned_to_user_id UUID,
    purchase_date       DATE,
    warranty_expiry     DATE,
    cost                NUMERIC(12, 2),
    vendor              VARCHAR(255),
    location            VARCHAR(255),
    supplier_id         UUID,
    notes               TEXT,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT assets_pk PRIMARY KEY (id),
    CONSTRAINT assets_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT assets_owner_user_id_fk FOREIGN KEY (owner_user_id)
        REFERENCES shrine.users(id),
    CONSTRAINT assets_assigned_to_user_id_fk FOREIGN KEY (assigned_to_user_id)
        REFERENCES shrine.users(id),
    CONSTRAINT assets_supplier_id_fk FOREIGN KEY (supplier_id)
        REFERENCES korur.suppliers(id),
    CONSTRAINT assets_asset_tag_org_uq UNIQUE (asset_tag, organization_id)
);

-- =====================================================
-- 3. Create asset_assignments table
-- =====================================================

CREATE TABLE korur.asset_assignments (
    id                  UUID        NOT NULL,
    organization_id     UUID        NOT NULL,
    asset_id            UUID        NOT NULL,
    assigned_to_user_id UUID        NOT NULL,
    assigned_to_team    VARCHAR(255),
    purpose             TEXT,
    start_date          DATE        NOT NULL,
    end_date            DATE,
    status              korur.asset_assignment_status_enum NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT asset_assignments_pk PRIMARY KEY (id),
    CONSTRAINT asset_assignments_asset_id_fk FOREIGN KEY (asset_id)
        REFERENCES korur.assets(id) ON DELETE CASCADE,
    CONSTRAINT asset_assignments_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT asset_assignments_assigned_to_user_id_fk FOREIGN KEY (assigned_to_user_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- Assets indexes
CREATE INDEX assets_organization_id_idx ON korur.assets(organization_id);
CREATE INDEX assets_asset_type_idx ON korur.assets(asset_type);
CREATE INDEX assets_status_idx ON korur.assets(status);
CREATE INDEX assets_classification_idx ON korur.assets(classification);
CREATE INDEX assets_owner_user_id_idx ON korur.assets(owner_user_id);
CREATE INDEX assets_assigned_to_user_id_idx ON korur.assets(assigned_to_user_id);
CREATE INDEX assets_supplier_id_idx ON korur.assets(supplier_id);

-- Asset assignments indexes
CREATE INDEX asset_assignments_asset_id_idx ON korur.asset_assignments(asset_id);
CREATE INDEX asset_assignments_organization_id_idx ON korur.asset_assignments(organization_id);
CREATE INDEX asset_assignments_assigned_to_user_id_idx ON korur.asset_assignments(assigned_to_user_id);
CREATE INDEX asset_assignments_status_idx ON korur.asset_assignments(status);
