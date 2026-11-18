-- Village Database Schema - Create Backupables Table
-- V1_0_2__Create_Backupables_Table.sql
--
-- Backupable domain table for backup resource tracking.
-- Part of the Korur app (cybersecurity nervous system).

-- =====================================================
-- 1. Table
-- =====================================================

CREATE TABLE korur.backupables (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    title               VARCHAR(255) NOT NULL,
    locations           JSONB NULL,
    amount_of_backups   INTEGER NOT NULL DEFAULT 0,
    backup_type         VARCHAR(255) NOT NULL,
    next_backup         TIMESTAMP WITH TIME ZONE NULL,
    latest_backup       TIMESTAMP WITH TIME ZONE NULL,
    owner_user_id       UUID NOT NULL,
    organization_id     UUID NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT backupables_pk PRIMARY KEY (id),
    CONSTRAINT backupables_owner_user_id_fk FOREIGN KEY (owner_user_id) REFERENCES shrine.users (id),
    CONSTRAINT backupables_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id)
);

-- =====================================================
-- 2. Indexes
-- =====================================================

CREATE INDEX backupables_owner_user_id_idx ON korur.backupables (owner_user_id);
CREATE INDEX backupables_organization_id_idx ON korur.backupables (organization_id);
