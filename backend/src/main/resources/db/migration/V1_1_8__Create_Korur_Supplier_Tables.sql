-- Korur Database Schema - Create Supplier Tables
-- V1_2_5__Create_Korur_Supplier_Tables.sql
--
-- Phase 5: Creates ISO 27001 supplier management tables:
-- suppliers and supplier_assessments.
-- Supplier assessments reuse korur.risk_risk_level_enum from V1_2_1.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE korur.supplier_supplier_type_enum AS ENUM (
    'CLOUD_PROVIDER',
    'SOFTWARE_VENDOR',
    'CONSULTANCY',
    'HOSTING',
    'SAAS',
    'OTHER'
);

CREATE TYPE korur.supplier_supplier_criticality_enum AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL'
);

CREATE TYPE korur.supplier_supplier_status_enum AS ENUM (
    'ACTIVE',
    'UNDER_REVIEW',
    'SUSPENDED',
    'TERMINATED'
);

CREATE TYPE korur.supplier_data_classification_enum AS ENUM (
    'PUBLIC',
    'INTERNAL',
    'CONFIDENTIAL',
    'RESTRICTED'
);

-- =====================================================
-- 2. Create suppliers table
-- =====================================================

CREATE TABLE korur.suppliers (
    id                    UUID         NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    supplier_type         korur.supplier_supplier_type_enum        NOT NULL,
    criticality           korur.supplier_supplier_criticality_enum NOT NULL,
    contact_name          VARCHAR(255),
    contact_email         VARCHAR(254),
    website               VARCHAR(512),
    data_access           TEXT,
    data_classification   korur.supplier_data_classification_enum,
    data_location         VARCHAR(255),
    dpa_in_place          BOOLEAN      NOT NULL DEFAULT FALSE,
    dpa_signed_at         TIMESTAMP WITH TIME ZONE,
    contract_reference    VARCHAR(255),
    contract_expiry_date  TIMESTAMP WITH TIME ZONE,
    status                korur.supplier_supplier_status_enum      NOT NULL DEFAULT 'ACTIVE',
    last_assessment_at    TIMESTAMP WITH TIME ZONE,
    next_assessment_due   TIMESTAMP WITH TIME ZONE,
    organization_id       UUID         NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT suppliers_pk PRIMARY KEY (id)
);

-- =====================================================
-- 3. Create supplier_assessments table
-- =====================================================

CREATE TABLE korur.supplier_assessments (
    id                        UUID          NOT NULL,
    supplier_id               UUID          NOT NULL,
    assessor_id               UUID          NOT NULL,
    assessed_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    risk_level                korur.risk_risk_level_enum NOT NULL,
    summary                   TEXT          NOT NULL,
    security_certifications   VARCHAR(1000),
    findings                  TEXT,
    recommended_actions       TEXT,
    deleted                   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT supplier_assessments_pk PRIMARY KEY (id),
    CONSTRAINT supplier_assessments_supplier_id_fk FOREIGN KEY (supplier_id)
        REFERENCES korur.suppliers(id),
    CONSTRAINT supplier_assessments_assessor_id_fk FOREIGN KEY (assessor_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

CREATE INDEX suppliers_organization_id_status_idx
    ON korur.suppliers(organization_id, status);

CREATE INDEX supplier_assessments_supplier_id_idx
    ON korur.supplier_assessments(supplier_id);
