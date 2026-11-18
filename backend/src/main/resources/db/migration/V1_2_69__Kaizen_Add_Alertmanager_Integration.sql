-- Database Schema - Kaizen Add Alertmanager Integration
-- V1_2_69__Kaizen_Add_Alertmanager_Integration.sql
--
-- An Alertmanager notification becomes a Kaizen ticket. A ticket created by an
-- integration has no person as creator, so a system actor table holds the
-- non-human creators and a ticket points to exactly one creator, either a user
-- or a system actor. The ticket also stores the key its source gives the
-- incident, so that a repeated firing notification finds the open ticket of
-- the same incident on the board by that key instead of creating a second one.
-- Resolved notifications are ignored and do not look up the open ticket.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE kaizen_system_actor_type_enum AS ENUM ('ALERTMANAGER');

-- =====================================================
-- 2. Create tables
-- =====================================================

-- One row per kind of system actor, enforced by the unique type constraint.
-- A system actor has no Keycloak account and no board membership: it cannot
-- sign in, and the webhook writes only to the board named in its configuration.
CREATE TABLE kaizen.system_actors (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type kaizen_system_actor_type_enum NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT system_actors_pk PRIMARY KEY (id),
    CONSTRAINT system_actors_type_uk UNIQUE (type)
);

-- =====================================================
-- 3. Seed the Alertmanager system actor
-- =====================================================

-- The application finds the actor by its type, not by id, so the id is
-- generated. ON CONFLICT (type) keeps the seed safe to re-run.
INSERT INTO kaizen.system_actors (id, name, type)
VALUES (gen_random_uuid(), 'Alertmanager', 'ALERTMANAGER')
ON CONFLICT (type) DO NOTHING;

-- =====================================================
-- 4. Alter kaizen.tickets: system actor creator
-- =====================================================

-- A system actor ticket has no user creator, so created_by_id becomes nullable.
ALTER TABLE kaizen.tickets ALTER COLUMN created_by_id DROP NOT NULL;

-- No delete rule: the default NO ACTION blocks deleting a system actor that
-- still has tickets, so a ticket never loses its creator.
ALTER TABLE kaizen.tickets ADD COLUMN created_by_system_actor_id UUID NULL;

ALTER TABLE kaizen.tickets ADD CONSTRAINT tickets_created_by_system_actor_id_fk
    FOREIGN KEY (created_by_system_actor_id) REFERENCES kaizen.system_actors (id);

-- Exactly one creator: a user or a system actor, never both and never neither.
ALTER TABLE kaizen.tickets ADD CONSTRAINT tickets_creator_ck
    CHECK (num_nonnulls(created_by_id, created_by_system_actor_id) = 1);

-- =====================================================
-- 5. Alter kaizen.tickets: incident key
-- =====================================================

-- Null for every ticket that does not come from an incident source. 64
-- characters leave room beyond the 16 hex characters Alertmanager produces.
ALTER TABLE kaizen.tickets ADD COLUMN incident_key VARCHAR(64) NULL;

-- =====================================================
-- 6. Create indexes
-- =====================================================

-- Foreign key lookup. Partial because almost every ticket has a user creator,
-- and those rows would only add index size.
CREATE INDEX tickets_created_by_system_actor_id_idx ON kaizen.tickets (created_by_system_actor_id) WHERE created_by_system_actor_id IS NOT NULL;

-- Lookup of the open ticket of an incident on a board. Partial because almost
-- every ticket has no incident key. Not unique: a closed ticket and a new one
-- may share a key.
CREATE INDEX tickets_board_id_incident_key_idx ON kaizen.tickets (board_id, incident_key) WHERE incident_key IS NOT NULL;
