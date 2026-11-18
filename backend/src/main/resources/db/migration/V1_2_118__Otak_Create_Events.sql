-- Oba Database Schema - Otak: Create Events
-- V1_2_118__Otak_Create_Events.sql
--
-- Adds the event of an organization to Otak: an expo, conference, meetup or
-- webinar with a start date, an optional end date and a place. A relation may
-- point at the event it was met at.
--
-- The enum type is created in the public schema with an otak_ prefix, as in
-- V1_2_100. The country reuses otak_country_enum of V1_2_105. Dates are DATE
-- values: an event happens on a calendar day, not at an instant.
--
-- Tenant separation: events_id_organization_id_uk exists only as the target of
-- the composite foreign key of relations, so a relation can never point to an
-- event of another organization.
--
-- Delete rules:
--   events_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its events.
--   relations_event_id_organization_id_fk ON DELETE SET NULL (event_id):
--     deleting an event clears only event_id of its relations (PostgreSQL 15
--     and later); the relations and their organization_id stay.
--
-- Indexes:
--   events_organization_id_lower_name_uk is unique per organization on the
--     lower-case name, which enforces the name rule, serves the name search,
--     and serves the organization foreign key and its cascade.
--   events_organization_id_start_date_idx serves the upcoming and past lists,
--     which filter one organization and order by start date.
--   relations_event_id_idx serves the foreign key of the event link, the
--     filter of relations by event and the clearing of the link when an event
--     is deleted.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_event_type_enum AS ENUM ('EXPO', 'CONFERENCE', 'MEETUP', 'WEBINAR', 'OTHER');

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE otak.events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    type otak_event_type_enum NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    city VARCHAR(100),
    country otak_country_enum,
    website VARCHAR(500),
    notes TEXT,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT events_pk PRIMARY KEY (id),
    CONSTRAINT events_id_organization_id_uk UNIQUE (id, organization_id),
    CONSTRAINT events_dates_ck CHECK ((end_date IS NULL OR end_date >= start_date)),
    CONSTRAINT events_notes_ck CHECK ((char_length(notes) <= 10000)),
    CONSTRAINT events_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX events_organization_id_lower_name_uk ON otak.events USING btree (organization_id, lower((name)::text));
CREATE INDEX events_organization_id_start_date_idx ON otak.events USING btree (organization_id, start_date);

-- =====================================================
-- 4. Alter relations
-- =====================================================

ALTER TABLE otak.relations
    ADD COLUMN event_id UUID,
    ADD CONSTRAINT relations_event_id_organization_id_fk FOREIGN KEY (event_id, organization_id) REFERENCES otak.events(id, organization_id) ON DELETE SET NULL (event_id);

CREATE INDEX relations_event_id_idx ON otak.relations USING btree (event_id);
