-- Oba Database Schema - Otak: Create Schema
-- V1_2_100__Otak_Create_Schema.sql
--
-- Adds the Otak app, the CRM of an organization, to the platform database: one
-- schema, otak, with the organization's relations (the companies it sells to,
-- buys from or tries to win), their contacts, and the timeline of activities
-- per relation. Otak keeps no users or organizations of its own: every user
-- reference points at shrine.users(id) and every organization reference at
-- shrine.organizations(id). Rows are removed by hard delete.
--
-- Enum types are created in the public schema with an otak_ prefix, as the
-- other apps of this set do.
--
-- Tenant separation: relations_id_organization_id_uk exists only as the target
-- of the composite foreign keys of contacts and activities, so a contact or an
-- activity can never point to a relation of another organization.
--
-- Delete rules:
--   relations_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its relations, and through them their contacts and activities.
--   relations_owner_user_id_fk ON DELETE SET NULL: a relation outlives its owner.
--   contacts_relation_id_organization_id_fk and
--   activities_relation_id_organization_id_fk ON DELETE CASCADE: deleting a
--     relation deletes its contacts and its timeline.
--   activities_author_user_id_fk ON DELETE SET NULL: an entry outlives its author.
--
-- Indexes:
--   relations_organization_id_type_stage_idx serves the list tabs, the stage
--     filter and the board, and the cascade of the organization foreign key.
--   relations_organization_id_updated_at_idx serves the list order, newest
--     change first.
--   relations_organization_id_lower_name_idx serves the case-insensitive prefix
--     search on name; a contains search filters the organization's rows.
--   relations_owner_user_id_idx serves the owner filter and the user foreign key.
--   contacts_relation_id_idx serves the contacts of a relation and the cascade
--     of the composite foreign key.
--   activities_relation_id_occurred_on_created_at_idx serves the timeline order
--     and the cascade of the composite foreign key.
--   activities_author_user_id_idx serves the user foreign key.

-- =====================================================
-- 1. Create schema
-- =====================================================

CREATE SCHEMA IF NOT EXISTS otak;

-- =====================================================
-- 2. Create enums
-- =====================================================

CREATE TYPE otak_relation_type_enum AS ENUM ('LEAD', 'SUPPLIER', 'CUSTOMER');
CREATE TYPE otak_lead_stage_enum AS ENUM ('NEW', 'QUALIFIED', 'CONTACTED', 'WARM', 'PROPOSAL', 'LOST', 'PARKED');
CREATE TYPE otak_relation_size_enum AS ENUM ('SIZE_1_10', 'SIZE_11_50', 'SIZE_51_200', 'SIZE_201_500', 'SIZE_501_1000', 'SIZE_1001_5000', 'SIZE_5001_PLUS');
CREATE TYPE otak_relation_source_enum AS ENUM ('NETWORK', 'LINKEDIN', 'REFERRAL', 'INBOUND', 'PARTNER', 'EVENT', 'RESEARCH', 'OTHER');
CREATE TYPE otak_activity_type_enum AS ENUM ('NOTE', 'CALL', 'MAIL', 'MEETING', 'CHANGE');

-- =====================================================
-- 3. Create tables
-- =====================================================

CREATE TABLE otak.relations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    website VARCHAR(500),
    sector VARCHAR(100),
    registration_number VARCHAR(50),
    vat_number VARCHAR(50),
    street VARCHAR(200),
    postal_code VARCHAR(20),
    city VARCHAR(100),
    country VARCHAR(2),
    expected_value NUMERIC(12,2),
    do_not_contact BOOLEAN NOT NULL DEFAULT false,
    notes TEXT,
    type otak_relation_type_enum NOT NULL,
    stage otak_lead_stage_enum,
    size otak_relation_size_enum,
    source otak_relation_source_enum,
    organization_id UUID NOT NULL,
    owner_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT relations_pk PRIMARY KEY (id),
    CONSTRAINT relations_id_organization_id_uk UNIQUE (id, organization_id),
    CONSTRAINT relations_stage_ck CHECK (((type = 'LEAD'::otak_relation_type_enum) = (stage IS NOT NULL))),
    CONSTRAINT relations_expected_value_ck CHECK ((expected_value >= (0)::numeric)),
    CONSTRAINT relations_notes_ck CHECK ((char_length(notes) <= 10000)),
    CONSTRAINT relations_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT relations_owner_user_id_fk FOREIGN KEY (owner_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

CREATE TABLE otak.contacts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    role VARCHAR(100),
    email VARCHAR(254),
    phone VARCHAR(50),
    linkedin_url VARCHAR(500),
    decision_maker BOOLEAN NOT NULL DEFAULT false,
    organization_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT contacts_pk PRIMARY KEY (id),
    CONSTRAINT contacts_relation_id_organization_id_fk FOREIGN KEY (relation_id, organization_id) REFERENCES otak.relations(id, organization_id) ON DELETE CASCADE
);

CREATE TABLE otak.activities (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    occurred_on DATE NOT NULL,
    text VARCHAR(5000) NOT NULL,
    type otak_activity_type_enum NOT NULL,
    organization_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    author_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT activities_pk PRIMARY KEY (id),
    CONSTRAINT activities_relation_id_organization_id_fk FOREIGN KEY (relation_id, organization_id) REFERENCES otak.relations(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT activities_author_user_id_fk FOREIGN KEY (author_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

CREATE INDEX relations_organization_id_type_stage_idx ON otak.relations USING btree (organization_id, type, stage);
CREATE INDEX relations_organization_id_updated_at_idx ON otak.relations USING btree (organization_id, updated_at DESC);
CREATE INDEX relations_organization_id_lower_name_idx ON otak.relations USING btree (organization_id, lower((name)::text) text_pattern_ops);
CREATE INDEX relations_owner_user_id_idx ON otak.relations USING btree (owner_user_id);
CREATE INDEX contacts_relation_id_idx ON otak.contacts USING btree (relation_id);
CREATE INDEX activities_relation_id_occurred_on_created_at_idx ON otak.activities USING btree (relation_id, occurred_on DESC, created_at DESC);
CREATE INDEX activities_author_user_id_idx ON otak.activities USING btree (author_user_id);
