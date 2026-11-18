-- Village Database Schema - Create Korur Schema And Kaizen Integration
-- V1_0_1__Create_Kutalmis_And_Sage_Tables.sql
--
-- Creates the korur schema for backup/workload infrastructure and adds
-- the board_member_specialties join table for the Kaizen domain, plus
-- the security_board_id FK on shrine.organizations.
--
-- Note: Sage schema and all sage/kutalmis tables have been consolidated
-- into V1_1_18__Create_Sage_Schema.sql. This file retains only the
-- non-sage content originally bundled here.

-- =====================================================
-- 1. Schemas
-- =====================================================

CREATE SCHEMA IF NOT EXISTS korur;

-- =====================================================
-- 2. Kaizen integration: board member specialties
-- =====================================================

-- Board member specialties join table
CREATE TABLE kaizen.board_member_specialties (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    board_member_id UUID NOT NULL,
    specialty       VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT board_member_specialties_pk PRIMARY KEY (id),
    CONSTRAINT board_member_specialties_board_member_id_specialty_uk UNIQUE (board_member_id, specialty),
    CONSTRAINT board_member_specialties_board_member_id_fk FOREIGN KEY (board_member_id)
        REFERENCES kaizen.board_members (id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Security board designation on Organization
-- =====================================================

ALTER TABLE shrine.organizations ADD COLUMN security_board_id UUID NULL;

ALTER TABLE shrine.organizations ADD CONSTRAINT organizations_security_board_id_fk
    FOREIGN KEY (security_board_id) REFERENCES kaizen.boards (id) ON DELETE SET NULL;

-- =====================================================
-- 4. Indexes
-- =====================================================

-- Board member specialties
CREATE INDEX board_member_specialties_board_member_id_idx ON kaizen.board_member_specialties (board_member_id);
CREATE INDEX board_member_specialties_specialty_idx ON kaizen.board_member_specialties (specialty);

-- Organizations security board
CREATE INDEX organizations_security_board_id_idx ON shrine.organizations (security_board_id);
