-- Oba Database Schema - Kazan: Create Cyberscore
-- V1_2_133__Kazan_Create_Cyberscore.sql
--
-- Every organization keeps a list of security checks, each with a weight, an
-- optional category, an outcome and a workflow status. The organization has one
-- live cyberscore computed from its checks (kazan.cyberscores, one row per
-- organization), and an append-only history of that score (kazan.cyberscore_snapshots)
-- that feeds the trend chart. The status is workflow only and never enters the
-- score. Snapshots keep no link to a check, so they survive deleting one.
--
-- Delete rules:
--   checks_organization_id_fk, cyberscores_organization_id_fk and
--     cyberscore_snapshots_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its checks, its score and its history.
--   cyberscore_snapshots_performed_by_id_fk ON DELETE SET NULL: the history
--     outlives the user who caused a snapshot, so only the author is cleared.
--
-- Indexes:
--   checks_organization_id_name_uk enforces one check name per organization;
--     leading with organization_id it also serves the list of an organization's
--     checks and the organization foreign key cascade.
--   cyberscores_organization_id_uk enforces one score row per organization, serves
--     the lookup and the row lock of the write flow, and the organization cascade.
--   cyberscore_snapshots_organization_id_taken_at_idx serves the trend query
--     (one organization, a time range, ordered by time) and the organization
--     cascade.
--   cyberscore_snapshots_performed_by_id_idx serves the user foreign key
--     (SET NULL on user delete).

-- =====================================================
-- 1. Create enums
-- =====================================================

-- The declaration order of kazan_check_category_enum is the display order.
CREATE TYPE kazan_check_category_enum AS ENUM (
    'NETWORK_OFFICE',
    'CLOUDFLARE_DNS',
    'MICROSOFT_365',
    'SHAREPOINT_ONEDRIVE',
    'PASSWORDS_RBAC',
    'ACCOUNTING_SOFTWARE',
    'DEVICES',
    'STORAGE_BACKUPS_RETENTION',
    'GDPR_AVG'
);
CREATE TYPE kazan_check_outcome_enum AS ENUM ('TODO', 'PASS', 'PARTIAL', 'FAIL', 'NO_LICENSE', 'NOT_APPLICABLE');
CREATE TYPE kazan_check_status_enum AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'DONE', 'NOT_APPLICABLE');
CREATE TYPE kazan_cyberscore_level_enum AS ENUM ('UNKNOWN', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'MINIMAL');
CREATE TYPE kazan_cyberscore_snapshot_event_type_enum AS ENUM (
    'CHECK_CREATED',
    'CHECK_UPDATED',
    'CHECK_OUTCOME_CHANGED',
    'CHECK_DELETED'
);

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE kazan.checks (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(5000) NULL,
    expected VARCHAR(5000) NOT NULL,
    weight INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    category kazan_check_category_enum NULL,
    outcome kazan_check_outcome_enum NOT NULL DEFAULT 'TODO',
    status kazan_check_status_enum NOT NULL DEFAULT 'NOT_STARTED',
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT checks_pk PRIMARY KEY (id),
    CONSTRAINT checks_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT checks_organization_id_name_uk UNIQUE (organization_id, name),
    CONSTRAINT checks_weight_ck CHECK (weight BETWEEN 1 AND 100),
    CONSTRAINT checks_sort_order_ck CHECK (sort_order BETWEEN 0 AND 9999)
);

-- One row per organization, created on the first scoring change. The
-- breakdown maps a category name to its percentage.
CREATE TABLE kazan.cyberscores (
    id UUID NOT NULL,
    score_percentage INTEGER NOT NULL DEFAULT 0,
    breakdown_by_category JSONB NOT NULL DEFAULT '{}'::jsonb,
    score_level kazan_cyberscore_level_enum NOT NULL DEFAULT 'UNKNOWN',
    organization_id UUID NOT NULL,
    last_calculated_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cyberscores_pk PRIMARY KEY (id),
    CONSTRAINT cyberscores_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT cyberscores_organization_id_uk UNIQUE (organization_id),
    CONSTRAINT cyberscores_score_percentage_ck CHECK (score_percentage BETWEEN 0 AND 100)
);

-- Append only: the application inserts and never updates or deletes a row.
CREATE TABLE kazan.cyberscore_snapshots (
    id UUID NOT NULL,
    score_percentage INTEGER NOT NULL,
    breakdown_by_category JSONB NOT NULL DEFAULT '{}'::jsonb,
    score_level kazan_cyberscore_level_enum NOT NULL,
    event_type kazan_cyberscore_snapshot_event_type_enum NOT NULL,
    organization_id UUID NOT NULL,
    performed_by_id UUID NULL,
    taken_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cyberscore_snapshots_pk PRIMARY KEY (id),
    CONSTRAINT cyberscore_snapshots_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT cyberscore_snapshots_performed_by_id_fk FOREIGN KEY (performed_by_id) REFERENCES shrine.users (id) ON DELETE SET NULL,
    CONSTRAINT cyberscore_snapshots_score_percentage_ck CHECK (score_percentage BETWEEN 0 AND 100)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE INDEX cyberscore_snapshots_organization_id_taken_at_idx ON kazan.cyberscore_snapshots (organization_id, taken_at);
CREATE INDEX cyberscore_snapshots_performed_by_id_idx ON kazan.cyberscore_snapshots (performed_by_id);
