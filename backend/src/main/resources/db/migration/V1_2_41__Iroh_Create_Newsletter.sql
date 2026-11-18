-- Village Database Schema - Iroh Create Newsletter
-- V1_2_41__Iroh_Create_Newsletter.sql
--
-- Adds the newsletter table and status enum for the Iroh sales domain.
--
-- A newsletter represents a bulk email campaign sent to an organization's
-- customer base from within the Iroh sales module. Each newsletter is scoped
-- to a single organization and progresses from DRAFT to SENT.
--
-- Tables created:
--   iroh.newsletters
--
-- Enums created:
--   iroh_newsletter_status_enum  (DRAFT, SENT)
--
-- Indexes:
--   newsletters_organization_id_idx          — FK lookup + soft-delete filter
--   newsletters_organization_id_status_idx   — status filter within an org
--   newsletters_organization_id_created_at_idx — ordered list view within an org
--
-- Rollback (manual, in reverse order):
--   DROP INDEX IF EXISTS iroh.newsletters_organization_id_created_at_idx;
--   DROP INDEX IF EXISTS iroh.newsletters_organization_id_status_idx;
--   DROP INDEX IF EXISTS iroh.newsletters_organization_id_idx;
--   DROP TABLE IF EXISTS iroh.newsletters;
--   DROP TYPE IF EXISTS iroh_newsletter_status_enum;

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE iroh_newsletter_status_enum AS ENUM ('DRAFT', 'SENT');

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE iroh.newsletters (
    id               UUID                         NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID                         NOT NULL,
    title            VARCHAR(255)                 NOT NULL,
    subject          VARCHAR(255)                 NOT NULL,
    content          TEXT                         NOT NULL,
    status           iroh_newsletter_status_enum  NOT NULL DEFAULT 'DRAFT',
    sent_at          TIMESTAMP WITH TIME ZONE,
    recipient_count  INT,
    created_at       TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    deleted          BOOLEAN                      NOT NULL DEFAULT FALSE,
    CONSTRAINT newsletters_pk PRIMARY KEY (id),
    CONSTRAINT newsletters_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id) ON DELETE RESTRICT
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- Required by convention: index the FK column.
-- Partial index on active (non-deleted) rows only — deleted newsletters
-- are never queried through the FK in normal operation.
CREATE INDEX newsletters_organization_id_idx
    ON iroh.newsletters(organization_id)
    WHERE deleted = FALSE;

-- Composite index supporting status-filtered list queries within an org
-- (e.g. "all DRAFT newsletters for org X").
CREATE INDEX newsletters_organization_id_status_idx
    ON iroh.newsletters(organization_id, status)
    WHERE deleted = FALSE;

-- Composite index supporting the default ordered list view within an org
-- (e.g. "most recent newsletters for org X"). DESC on created_at matches
-- the typical ORDER BY direction used by the query service.
CREATE INDEX newsletters_organization_id_created_at_idx
    ON iroh.newsletters(organization_id, created_at DESC)
    WHERE deleted = FALSE;
