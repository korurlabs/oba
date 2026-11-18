-- Database Schema - Account Lifecycle Soft Delete (Consolidated)
-- V1_2_0__Account_Lifecycle_Soft_Delete.sql
--
-- Consolidates all account lifecycle schema changes into a single migration:
-- adds DISABLED to user status enum, adds soft-delete columns, migrates DELETED
-- users to soft-deleted DISABLED, removes DELETED from the enum, creates partial
-- indexes, updates FK cascade rules, creates the dojo schema and audit_logs table,
-- and migrates betik audit data to dojo.
--
-- flyway:executeInTransaction=false is required because ALTER TYPE ADD VALUE cannot
-- run inside a transaction. Subsequent statements run in auto-commit mode.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Add soft-delete columns
-- =====================================================

ALTER TABLE shrine.users
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shrine.organizations
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shrine.organization_members
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
-- =====================================================
-- 2. Remove default constraint that depends on the old enum
-- =====================================================

ALTER TABLE shrine.users 
    ALTER COLUMN status DROP DEFAULT;

-- =====================================================
-- 3. Change column to text temporarily
-- =====================================================

ALTER TABLE shrine.users 
    ALTER COLUMN status TYPE VARCHAR(50);

-- =====================================================
-- 4. Drop old enum and create new one (without DELETED)
-- =====================================================

DROP TYPE IF EXISTS shrine_identity_user_status_enum;

CREATE TYPE shrine_identity_user_status_enum AS ENUM (
    'PENDING_ONBOARDING', 
    'ACTIVE', 
    'DISABLED'
);

-- =====================================================
-- 5. Convert column back to new enum + migrate DELETED → DISABLED
-- =====================================================

ALTER TABLE shrine.users 
    ALTER COLUMN status TYPE shrine_identity_user_status_enum 
    USING CASE 
        WHEN status = 'DELETED'            THEN 'DISABLED'::shrine_identity_user_status_enum
        WHEN status = 'PENDING_ONBOARDING' THEN 'PENDING_ONBOARDING'::shrine_identity_user_status_enum
        WHEN status = 'ACTIVE'             THEN 'ACTIVE'::shrine_identity_user_status_enum
        WHEN status = 'DISABLED'           THEN 'DISABLED'::shrine_identity_user_status_enum
        ELSE 'ACTIVE'::shrine_identity_user_status_enum
    END;

-- =====================================================
-- 6. Re-add default value (if you had one originally)
-- =====================================================

ALTER TABLE shrine.users 
    ALTER COLUMN status SET DEFAULT 'ACTIVE'::shrine_identity_user_status_enum;

-- =====================================================
-- 7. Convert unique constraints to partial indexes (soft-delete aware)
-- =====================================================

-- organization_members: allow re-invite after soft-delete
ALTER TABLE shrine.organization_members
    DROP CONSTRAINT IF EXISTS organization_members_organization_id_user_id_uk;
CREATE UNIQUE INDEX organization_members_organization_id_user_id_uk
    ON shrine.organization_members (organization_id, user_id) WHERE deleted = FALSE;

-- users: allow email reuse after soft-delete (email is anonymized on delete)
ALTER TABLE shrine.users DROP CONSTRAINT IF EXISTS users_email_uk;
CREATE UNIQUE INDEX users_email_uk ON shrine.users (email) WHERE deleted = FALSE;

-- =====================================================
-- 8. Partial indexes for soft-delete filtering
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_users_not_deleted
    ON shrine.users (id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_organizations_not_deleted
    ON shrine.organizations (id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_org_members_not_deleted
    ON shrine.organization_members (id) WHERE deleted = FALSE;

-- =====================================================
-- 9. Drop ON DELETE CASCADE from organization_members FKs
--    Soft delete is now managed by JPA cascade, not DB cascade.
-- =====================================================

ALTER TABLE shrine.organization_members
    DROP CONSTRAINT IF EXISTS organization_members_organization_id_fk;
ALTER TABLE shrine.organization_members
    ADD CONSTRAINT organization_members_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id);

ALTER TABLE shrine.organization_members
    DROP CONSTRAINT IF EXISTS organization_members_user_id_fk;
ALTER TABLE shrine.organization_members
    ADD CONSTRAINT organization_members_user_id_fk
        FOREIGN KEY (user_id) REFERENCES shrine.users (id);

-- =====================================================
-- 10. Create dojo schema and audit_logs table
-- =====================================================

CREATE SCHEMA IF NOT EXISTS dojo;

CREATE TABLE dojo.audit_logs (
    id              UUID NOT NULL,
    organization_id UUID NOT NULL,
    user_id         UUID,
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    old_values      JSONB,
    new_values      JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT dojo_audit_logs_pk PRIMARY KEY (id),
    CONSTRAINT dojo_audit_logs_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX dojo_audit_logs_organization_id_idx ON dojo.audit_logs(organization_id);
CREATE INDEX dojo_audit_logs_entity_type_entity_id_idx ON dojo.audit_logs(entity_type, entity_id);
CREATE INDEX dojo_audit_logs_created_at_idx ON dojo.audit_logs(created_at);

-- =====================================================
-- 11. Migrate betik audit logs to dojo and drop betik table
-- =====================================================

INSERT INTO dojo.audit_logs (id, organization_id, user_id, action, entity_type, entity_id,
                             old_values, new_values, ip_address, created_at, updated_at)
SELECT id, organization_id, user_id, action, entity_type, entity_id,
       old_values, new_values, ip_address, created_at, updated_at
FROM betik.audit_logs
ON CONFLICT (id) DO NOTHING;

DROP TABLE IF EXISTS betik.audit_logs CASCADE;
