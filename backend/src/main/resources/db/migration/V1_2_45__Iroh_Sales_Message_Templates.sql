-- Village Database Schema - Iroh Sales Message Templates
-- V1_2_45__Iroh_Sales_Message_Templates.sql
--
-- Creates the iroh.sales_message_templates table, which stores reusable outreach
-- message templates that admins create for their organisation. Claude selects the
-- best-fit template when generating a personalised draft for a new lead.
--
-- Design decisions:
--   - Soft-delete (deleted BOOLEAN) — templates with SENT messages must not be
--     hard-deleted; a service-level check enforces this, but the row is retained.
--   - tags is VARCHAR(1000) storing comma-separated values to avoid a junction
--     table for a low-cardinality, non-queryable attribute.
--   - created_by_id is nullable so templates can survive user deactivation.
--   - updated_at is nullable (NULL until first update) — matches BaseEntity pattern.

-- =====================================================
-- 1. Create table
-- =====================================================

CREATE TABLE iroh.sales_message_templates (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    subject          VARCHAR(500) NOT NULL,
    body             TEXT         NOT NULL,
    tone             iroh_message_tone_enum NOT NULL,
    language         VARCHAR(5)   NOT NULL DEFAULT 'nl',
    target_industry  VARCHAR(255),
    tags             VARCHAR(1000),
    version          INTEGER      NOT NULL DEFAULT 1,
    organization_id  UUID         NOT NULL,
    created_by_id    UUID,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT sales_message_templates_pk
        PRIMARY KEY (id),
    CONSTRAINT sales_message_templates_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT sales_message_templates_created_by_id_fk
        FOREIGN KEY (created_by_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Primary query path: list all templates for an organisation.
CREATE INDEX sales_message_templates_organization_id_idx
    ON iroh.sales_message_templates(organization_id);

-- FK index required by convention.
CREATE INDEX sales_message_templates_created_by_id_idx
    ON iroh.sales_message_templates(created_by_id);
