-- Oba Database Schema - Shrine Add Agent Detail To Users
-- V1_2_135__Shrine_Add_Agent_Detail_To_Users.sql
--
-- An agent is a shrine.users row of kind AGENT; there is no agent table. This
-- migration adds users.kind (HUMAN for every existing row) and the agent
-- detail columns, which stay NULL for a person.
--
-- users_kind_ck ties the two together: an AGENT row has role, flow, provider
-- and organization all set, and a HUMAN row has all four NULL, so a
-- half-filled agent or a person carrying any of that agent data cannot be
-- stored.
--
-- agent_organization_id is kept on the row (the agent has one membership)
-- because the check and the unique name index need it, and
-- default_organization_id is mutable and ON DELETE SET NULL.
--
-- Delete rules:
--   agent_organization_id      ON DELETE CASCADE: deleting an organization
--                              deletes its agent users (as memberships do).
--                              The check forbids SET NULL on an AGENT row.
--   agent_created_by_user_id   ON DELETE SET NULL: the agent outlives the
--                              person who registered it.
--
-- Indexes:
--   users_agent_created_by_user_id_idx             foreign key.
--   users_agent_organization_id_first_name_uk      one live agent name per
--                                                  organization, ignoring
--                                                  case (partial); its
--                                                  leading column also
--                                                  serves the foreign key.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE shrine_identity_user_kind_enum AS ENUM ('HUMAN', 'AGENT');
CREATE TYPE shrine_identity_agent_role_enum AS ENUM ('CYBERSECURITY_ENGINEER', 'SALES_ENGINEER');
CREATE TYPE shrine_identity_agent_flow_enum AS ENUM ('ROUTINE', 'API');
CREATE TYPE shrine_identity_agent_provider_enum AS ENUM ('ANTHROPIC', 'OPENAI', 'XAI', 'DEEPSEEK');

-- =====================================================
-- 2. Add columns
-- =====================================================

ALTER TABLE shrine.users
    ADD COLUMN kind shrine_identity_user_kind_enum NOT NULL DEFAULT 'HUMAN',
    ADD COLUMN agent_role shrine_identity_agent_role_enum,
    ADD COLUMN agent_flow shrine_identity_agent_flow_enum,
    ADD COLUMN agent_provider shrine_identity_agent_provider_enum,
    ADD COLUMN agent_organization_id UUID,
    ADD COLUMN agent_created_by_user_id UUID;

-- =====================================================
-- 3. Add constraints
-- =====================================================

ALTER TABLE shrine.users
    ADD CONSTRAINT users_agent_organization_id_fk FOREIGN KEY (agent_organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    ADD CONSTRAINT users_agent_created_by_user_id_fk FOREIGN KEY (agent_created_by_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL,
    ADD CONSTRAINT users_kind_ck CHECK (
        (kind = 'AGENT' AND agent_role IS NOT NULL AND agent_flow IS NOT NULL AND agent_provider IS NOT NULL AND agent_organization_id IS NOT NULL)
        OR (kind = 'HUMAN' AND agent_role IS NULL AND agent_flow IS NULL AND agent_provider IS NULL AND agent_organization_id IS NULL)
    );

-- =====================================================
-- 4. Create indexes
-- =====================================================

CREATE INDEX users_agent_created_by_user_id_idx ON shrine.users USING btree (agent_created_by_user_id);
CREATE UNIQUE INDEX users_agent_organization_id_first_name_uk ON shrine.users USING btree (agent_organization_id, lower(first_name)) WHERE kind = 'AGENT' AND deleted = FALSE;
