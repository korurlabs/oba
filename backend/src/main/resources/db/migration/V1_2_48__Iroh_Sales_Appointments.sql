-- Village Database Schema - Iroh Sales Appointments
-- V1_2_48__Iroh_Sales_Appointments.sql
--
-- Creates iroh.sales_appointments to track scheduled meetings, calls, demos,
-- and follow-ups linked to prospect relations. Reps create appointments once a
-- lead responds; the Appointments tab surfaces upcoming meetings sorted by
-- scheduled_at ascending.
--
-- Design decisions:
--   - relation_id is CASCADE DELETE: if a relation is hard-deleted all its
--     appointments are removed too. Relations are typically soft-deleted, so
--     this cascade fires only in administrative cleanup scenarios.
--   - assigned_user_id is nullable and SET NULL on user deletion: appointments
--     can exist without an assignee.
--   - duration_minutes and location are nullable: not all appointment types have
--     a fixed duration or physical location (e.g. async follow-ups).
--   - updated_at is nullable (NULL until first update) — matches BaseEntity pattern.

-- =====================================================
-- 1. Create table
-- =====================================================

CREATE TABLE iroh.sales_appointments (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    title             VARCHAR(255) NOT NULL,
    scheduled_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes  INTEGER,
    location          VARCHAR(500),
    notes             TEXT,
    appointment_type  iroh_appointment_type_enum NOT NULL,
    organization_id   UUID         NOT NULL,
    relation_id       UUID         NOT NULL,
    assigned_user_id  UUID,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT sales_appointments_pk
        PRIMARY KEY (id),
    CONSTRAINT sales_appointments_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT sales_appointments_relation_id_fk
        FOREIGN KEY (relation_id) REFERENCES iroh.relations(id) ON DELETE CASCADE,
    CONSTRAINT sales_appointments_assigned_user_id_fk
        FOREIGN KEY (assigned_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Primary list query: all upcoming appointments for an organisation sorted by date.
CREATE INDEX sales_appointments_organization_id_scheduled_at_idx
    ON iroh.sales_appointments(organization_id, scheduled_at);

-- Relation-scoped appointment list (shown on relation detail page).
CREATE INDEX sales_appointments_organization_id_relation_id_idx
    ON iroh.sales_appointments(organization_id, relation_id);

-- FK indexes required by convention.
CREATE INDEX sales_appointments_relation_id_idx
    ON iroh.sales_appointments(relation_id);

CREATE INDEX sales_appointments_assigned_user_id_idx
    ON iroh.sales_appointments(assigned_user_id);
