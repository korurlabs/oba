-- Oba Database Schema - Otak: Create Outreaches
-- V1_2_110__Otak_Create_Outreaches.sql
--
-- Adds the outreach of an organization to Otak: a reusable message, today a
-- mail with a subject and a plain-text body, that a user can pick when
-- reaching out to a lead. MAIL is the only type, so every outreach has a
-- subject and a body and both columns are NOT NULL.
--
-- The enum type is created in the public schema with an otak_ prefix, as in
-- V1_2_100.
--
-- Delete rules:
--   outreaches_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its outreaches.
--
-- Indexes:
--   outreaches_organization_id_lower_name_uk is unique per organization on the
--     lower-case name, which enforces the name rule, serves the name order and
--     search, and serves the organization foreign key and its cascade.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_outreach_type_enum AS ENUM ('MAIL');

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE otak.outreaches (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    type otak_outreach_type_enum NOT NULL,
    name VARCHAR(100) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT outreaches_pk PRIMARY KEY (id),
    CONSTRAINT outreaches_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX outreaches_organization_id_lower_name_uk ON otak.outreaches USING btree (organization_id, lower((name)::text));
