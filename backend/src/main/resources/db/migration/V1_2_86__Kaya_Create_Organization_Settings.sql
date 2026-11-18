-- Oba Database Schema - Kaya: Create Organization Settings
-- V1_2_86__Kaya_Create_Organization_Settings.sql
--
-- Kaya's per-organization retention used to live on its own organizations table,
-- which the platform replaces with shrine.organizations. This table holds the
-- Kaya settings of one platform organization, one row per organization, so the
-- platform table stays free of app columns. The row is removed with the
-- organization (ON DELETE CASCADE). The created_by and updated_by references are
-- cleared when the user is deleted (ON DELETE SET NULL).

-- =====================================================
-- 1. Create tables
-- =====================================================

CREATE TABLE kaya.organization_settings (
    organization_id UUID NOT NULL,
    retention_days INTEGER NOT NULL DEFAULT 180,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT organization_settings_pk PRIMARY KEY (organization_id),
    CONSTRAINT organization_settings_retention_days_ck CHECK (retention_days > 0),
    CONSTRAINT organization_settings_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_settings_created_by_fk FOREIGN KEY (created_by) REFERENCES shrine.users(id) ON DELETE SET NULL,
    CONSTRAINT organization_settings_updated_by_fk FOREIGN KEY (updated_by) REFERENCES shrine.users(id) ON DELETE SET NULL
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- The primary key covers organization_id. The user references are looked up when
-- a user is deleted, so each is indexed.
CREATE INDEX organization_settings_created_by_idx ON kaya.organization_settings (created_by);
CREATE INDEX organization_settings_updated_by_idx ON kaya.organization_settings (updated_by);
