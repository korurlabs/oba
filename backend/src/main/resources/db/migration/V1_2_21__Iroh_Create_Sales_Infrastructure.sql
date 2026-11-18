-- Village Database Schema - Iroh Create Sales Infrastructure
-- V1_2_19__Iroh_Create_Sales_Infrastructure.sql
--
-- Creates the sales CRM domain for the Iroh app: ICPs, Presentations, Leads, board columns,
-- and the presentation-tags junction table.
--
-- Table creation order is dependency-driven:
--   1. sales_icps            (no FK dependencies)
--   2. sales_presentations   (no FK dependencies)
--   3. sales_board_columns   (no FK dependencies; linked_status is a CHECK, not an FK)
--   4. sales_presentation_tags (FK → sales_presentations)
--   5. sales_leads           (FK → sales_icps, sales_presentations)
--
-- All tables:
--   - Live in the iroh schema (same domain as the finance module)
--   - Are scoped to organization_id (UUID FK → shrine.organizations) for multi-tenancy
--   - Carry soft-delete via deleted BOOLEAN NOT NULL DEFAULT FALSE
--   - Use TIMESTAMP WITH TIME ZONE for all audit timestamps
--   - Use PostgreSQL native enum types (iroh_{name}_enum) for JPA @JdbcType(PostgreSQLEnumJdbcType)
--
-- Cascade rules:
--   - Deleting an ICP or Presentation leaves leads intact (SET NULL) — leads retain their data
--   - Deleting a Presentation cascades to its tags (ON DELETE CASCADE)

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_lead_status_enum AS ENUM (
    'FIRST_CONTACT',
    'NEGOTIATION',
    'CONVERTED',
    'LOST',
    'INACTIVE'
);

CREATE TYPE iroh_presentation_type_enum AS ENUM (
    'PITCH',
    'PRODUCT_VIDEO',
    'COMPANY_VIDEO',
    'PORTFOLIO_ITEM',
    'CASE_STUDY'
);

-- =====================================================
-- 2. Create sales_icps table
-- =====================================================

CREATE TABLE iroh.sales_icps (
    id                       UUID NOT NULL DEFAULT gen_random_uuid(),
    name                     VARCHAR(255) NOT NULL,
    description              TEXT,
    target_industry          VARCHAR(255),
    target_company_size      VARCHAR(50),
    target_annual_revenue    VARCHAR(50),
    pain_points              TEXT,
    success_criteria         TEXT,
    recommended_approach     TEXT,
    estimated_contract_value INTEGER,
    sales_cycle_months       INTEGER,
    organization_id          UUID NOT NULL,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT sales_icps_pk PRIMARY KEY (id),
    CONSTRAINT sales_icps_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX sales_icps_organization_id_idx
    ON iroh.sales_icps(organization_id);

-- Supports the active ICP list view (only show is_active = true).
CREATE INDEX sales_icps_organization_id_is_active_idx
    ON iroh.sales_icps(organization_id, is_active);

CREATE INDEX sales_icps_organization_id_created_at_idx
    ON iroh.sales_icps(organization_id, created_at DESC);

-- =====================================================
-- 3. Create sales_presentations table
-- =====================================================

CREATE TABLE iroh.sales_presentations (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    type              iroh_presentation_type_enum NOT NULL,
    file_url          VARCHAR(2048),
    youtube_embed_id  VARCHAR(255),
    thumbnail_url     VARCHAR(2048),
    duration_seconds  INTEGER,
    target_audience   VARCHAR(255),
    key_messages      TEXT,
    organization_id   UUID NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT sales_presentations_pk PRIMARY KEY (id),
    CONSTRAINT sales_presentations_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX sales_presentations_organization_id_idx
    ON iroh.sales_presentations(organization_id);

-- Supports the active presentation library view.
CREATE INDEX sales_presentations_organization_id_is_active_idx
    ON iroh.sales_presentations(organization_id, is_active);

-- Supports filtering by type within an org (e.g. "show only PITCH items").
CREATE INDEX sales_presentations_organization_id_type_idx
    ON iroh.sales_presentations(organization_id, type);

-- =====================================================
-- 4. Create sales_board_columns table
-- =====================================================

-- Board columns are system-managed per organisation, each linked to a LeadStatus value.
-- Columns are inserted by the V1_2_20 migration and kept stable after that;
-- user reordering is not supported — column_order is display-only.

CREATE TABLE iroh.sales_board_columns (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    linked_status   iroh_lead_status_enum NOT NULL,
    column_order    INTEGER NOT NULL,
    organization_id UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT sales_board_columns_pk PRIMARY KEY (id),
    CONSTRAINT sales_board_columns_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    -- Each organisation has exactly one column per status (among non-deleted rows).
    CONSTRAINT sales_board_columns_organization_id_linked_status_uk
        UNIQUE (organization_id, linked_status)
);

CREATE INDEX sales_board_columns_organization_id_idx
    ON iroh.sales_board_columns(organization_id);

-- =====================================================
-- 5. Create sales_presentation_tags table
-- =====================================================

-- Junction table for @ElementCollection tags on Presentation.
-- Tags cascade-delete with their presentation.

CREATE TABLE iroh.sales_presentation_tags (
    presentation_id UUID NOT NULL,
    tag             VARCHAR(100) NOT NULL,
    CONSTRAINT sales_presentation_tags_pk PRIMARY KEY (presentation_id, tag),
    CONSTRAINT sales_presentation_tags_presentation_id_fk
        FOREIGN KEY (presentation_id) REFERENCES iroh.sales_presentations(id) ON DELETE CASCADE
);

CREATE INDEX sales_presentation_tags_presentation_id_idx
    ON iroh.sales_presentation_tags(presentation_id);

-- =====================================================
-- 6. Create sales_leads table
-- =====================================================

-- Created last because it has FK dependencies on sales_icps and sales_presentations.
-- icp_id and presentation_id are SET NULL on parent delete so that leads survive
-- ICP/Presentation removal without losing their contact and company data.

CREATE TABLE iroh.sales_leads (
    id                   UUID NOT NULL DEFAULT gen_random_uuid(),
    first_name           VARCHAR(255) NOT NULL,
    last_name            VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    phone_number         VARCHAR(30),
    company_name         VARCHAR(255) NOT NULL,
    industry             VARCHAR(255),
    company_website      VARCHAR(2048),
    annual_revenue       VARCHAR(50),
    employee_count       INTEGER,
    status               iroh_lead_status_enum NOT NULL,
    first_contact_date   DATE,
    last_interaction_date DATE,
    notes                TEXT,
    icp_id               UUID,
    presentation_id      UUID,
    board_order          INTEGER NOT NULL DEFAULT 0,
    organization_id      UUID NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT sales_leads_pk PRIMARY KEY (id),
    CONSTRAINT sales_leads_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT sales_leads_icp_id_fk
        FOREIGN KEY (icp_id) REFERENCES iroh.sales_icps(id) ON DELETE SET NULL,
    CONSTRAINT sales_leads_presentation_id_fk
        FOREIGN KEY (presentation_id) REFERENCES iroh.sales_presentations(id) ON DELETE SET NULL
);

-- Email uniqueness is per organisation (not globally unique): a lead's email
-- can exist in multiple orgs but only once per org among non-deleted rows.
CREATE UNIQUE INDEX sales_leads_organization_id_email_uk
    ON iroh.sales_leads(organization_id, email)
    WHERE deleted = FALSE;

CREATE INDEX sales_leads_organization_id_idx
    ON iroh.sales_leads(organization_id);

-- Primary board/list view filter: org + status.
CREATE INDEX sales_leads_organization_id_status_idx
    ON iroh.sales_leads(organization_id, status);

-- Date-range and sorted list queries.
CREATE INDEX sales_leads_organization_id_created_at_idx
    ON iroh.sales_leads(organization_id, created_at DESC);

-- Company name search.
CREATE INDEX sales_leads_company_name_idx
    ON iroh.sales_leads(company_name);

-- FK indexes (required by convention).
CREATE INDEX sales_leads_icp_id_idx
    ON iroh.sales_leads(icp_id);

CREATE INDEX sales_leads_presentation_id_idx
    ON iroh.sales_leads(presentation_id);

-- Supports "leads by ICP within org" query used by LeadRepository.
CREATE INDEX sales_leads_organization_id_icp_id_idx
    ON iroh.sales_leads(organization_id, icp_id);
