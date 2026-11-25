-- Oba Database Schema - Shrine Create Agent Routines
-- V1_2_138__Shrine_Create_Agent_Routines.sql
--
-- shrine.agent_routines holds the hosted routine that Oba fires for a ROUTINE
-- agent, and the encrypted secret that fires it. One row per agent. The vendor
-- is not stored: it is the agent's provider.
--
-- Only the application decrypts routine_secret_encrypted; routine_secret_prefix
-- is the short visible start of the secret for display.
--
-- Delete rules: deleting the agent user or its organization deletes the
-- routine (ON DELETE CASCADE on both keys).
--
-- Indexes:
--   agent_routines_user_id_uk          one routine per agent; lookup by agent.
--   agent_routines_organization_id_idx foreign key.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE shrine_agent_routine_fire_status_enum AS ENUM ('FIRED', 'FAILED', 'RATE_LIMITED', 'UNAUTHORIZED');

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE shrine.agent_routines (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    routine_id VARCHAR(128) NOT NULL,
    routine_secret_encrypted TEXT NOT NULL,
    routine_secret_prefix VARCHAR(16) NOT NULL,
    last_fired_at TIMESTAMP WITH TIME ZONE,
    last_fire_status shrine_agent_routine_fire_status_enum,
    last_http_status INTEGER,
    last_session_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT agent_routines_pk PRIMARY KEY (id),
    CONSTRAINT agent_routines_user_id_uk UNIQUE (user_id),
    CONSTRAINT agent_routines_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT agent_routines_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE INDEX agent_routines_organization_id_idx ON shrine.agent_routines USING btree (organization_id);
