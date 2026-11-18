-- Oba Database Schema - Kaizen: Create Organization Settings
-- V1_2_87__Kaizen_Create_Organization_Settings.sql
--
-- The security board is a Kaizen concept, so it leaves the platform table
-- shrine.organizations. This table holds the Kaizen settings of one platform
-- organization, one row per organization. The row is removed with the
-- organization (ON DELETE CASCADE). The security board reference is cleared when
-- the board is deleted (ON DELETE SET NULL), as organizations_security_board_id_fk
-- does today. The old column shrine.organizations.security_board_id stays in this
-- version, still read by the running code, and is dropped in a later release.

-- =====================================================
-- 1. Create tables
-- =====================================================

CREATE TABLE kaizen.organization_settings (
    organization_id UUID NOT NULL,
    security_board_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organization_settings_pk PRIMARY KEY (organization_id),
    CONSTRAINT organization_settings_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_settings_security_board_id_fk FOREIGN KEY (security_board_id) REFERENCES kaizen.boards(id) ON DELETE SET NULL
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- The primary key covers organization_id. The board reference is looked up when
-- a board is deleted and when the security board is resolved.
CREATE INDEX organization_settings_security_board_id_idx ON kaizen.organization_settings (security_board_id);

-- =====================================================
-- 3. Copy existing security boards
-- =====================================================

INSERT INTO kaizen.organization_settings (organization_id, security_board_id)
SELECT id, security_board_id
FROM shrine.organizations
WHERE security_board_id IS NOT NULL;
